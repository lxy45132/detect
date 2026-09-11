package com.detect.event.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.detect.common.core.enums.ResultCode;
import com.detect.common.core.exception.BizException;
import com.detect.common.oss.OssTemplate;
import com.detect.event.dto.EventReceiveDTO;
import com.detect.event.dto.EventRecordUpdateDTO;
import com.detect.event.entity.CameraManage;
import com.detect.event.entity.EventRecords;
import com.detect.event.enums.HandleStatusEnum;
import com.detect.event.enums.PriorityEnum;
import com.detect.event.mapper.CameraManageMapper;
import com.detect.event.mapper.EventRecordsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 事件记录写侧服务：receive(§4.1.1) / update(§4.1.4) / delete(§4.1.5) / batchDelete(§4.1.6)。
 * 查询侧(page/detail/statistics/export)见 {@link EventQueryService}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventRecordService {

    private static final ZoneOffset ZONE_EAST8 = ZoneOffset.ofHours(8);
    private static final String SNAP_DIR = "event";

    private final EventRecordsMapper eventRecordsMapper;
    private final CameraManageMapper cameraManageMapper;
    private final OssTemplate ossTemplate;

    /**
     * 接收 Python 推送的单条事件。
     *
     * <p>流程：snapImage(base64)→MinIO 写 snapUrl；按 deviceNum 查 camera_manage 补 deviceName；status=0；
     * handleStatus/priority 取默认(§5.3)；<b>幂等</b>(deviceNum + snapTime + sourceData.trackId)去重后入库。
     *
     * @return 事件 id；命中幂等则返回已存在 id(Python 据 code==0 停止重试)
     */
    public Long receive(EventReceiveDTO dto) {
        LocalDateTime snapTime = toEast8(dto.getSnapTime());
        Long trackId = extractTrackId(dto.getSourceData());

        // 幂等：同 device+snapTime+trackId 已存在则直接返回，短路 MinIO 转存与 insert，杜绝 Python 重试重复入库
        Long existing = findDuplicate(dto.getDeviceNum(), snapTime, trackId);
        if (existing != null) {
            log.info("[receive] 幂等命中，返回已存在事件 id={} device={} snapTime={} trackId={}",
                    existing, dto.getDeviceNum(), snapTime, trackId);
            return existing;
        }

        EventRecords entity = new EventRecords();
        entity.setDeviceNum(dto.getDeviceNum());
        entity.setDeviceName(resolveDeviceName(dto.getDeviceNum()));
        entity.setEventType(dto.getEventType());
        entity.setSnapTime(snapTime);
        entity.setSourceData(dto.getSourceData());
        entity.setStatus(0);
        entity.setSnapUrl(transferImage(dto.getSnapImage()));

        // 业务字段：Python 有则填，无则 null
        entity.setName(dto.getName());
        entity.setCardno(dto.getCardno());
        entity.setLibName(dto.getLibName());
        entity.setSimilarity(dto.getSimilarity());
        entity.setIdentifyFaceUrl(dto.getIdentifyFaceUrl());
        entity.setVisibleLightUrl(dto.getVisibleLightUrl());
        entity.setPlateNum(dto.getPlateNum());
        entity.setVehicleType(dto.getVehicleType());
        entity.setVehicleNormalType(dto.getVehicleNormalType());
        entity.setVehicleLogo(dto.getVehicleLogo());
        entity.setVehicleSubLogo(dto.getVehicleSubLogo());
        entity.setVehicleColor(dto.getVehicleColor());
        entity.setVehicleModel(dto.getVehicleModel());
        entity.setHeightPermitted(dto.getHeightPermitted());
        entity.setCrowdNum(dto.getCrowdNum());

        // 预警扩展默认值(§5.3)：入库默认未处理、普通优先级
        entity.setHandleStatus(HandleStatusEnum.PENDING.getCode());
        entity.setPriority(PriorityEnum.NORMAL.getCode());

        eventRecordsMapper.insert(entity);
        log.info("[receive] 事件入库 id={} device={} eventType={} snapTime={} snapUrl={}",
                entity.getId(), entity.getDeviceNum(), entity.getEventType(), snapTime, entity.getSnapUrl());

        // TODO(6c)：入库后 LPUSH eventId 到 Redis 轻量队列，异步触发布控规则匹配(不阻塞 webhook)
        return entity.getId();
    }

    /**
     * 修正事件业务字段(§4.1.4)。仅传需改字段，按非空增量更新；<b>不含 handleStatus</b>(状态流转走 §4.3)。
     * update_time 由 MetaObjectHandler 自动填充留痕。
     *
     * @throws BizException 1001 事件不存在(或已逻辑删除)
     */
    public void update(Long id, EventRecordUpdateDTO dto) {
        if (eventRecordsMapper.selectById(id) == null) {
            throw new BizException(ResultCode.EVENT_NOT_FOUND);
        }
        EventRecords upd = new EventRecords();
        upd.setId(id);
        // 仅拷贝 dto 中非空字段；EventRecords 的 updateById 默认 NOT_NULL 策略再次确保 null 字段不进 SET
        BeanUtil.copyProperties(dto, upd, CopyOptions.create().setIgnoreNullValue(true));
        eventRecordsMapper.updateById(upd);
        log.info("[update] 事件字段修正 id={}", id);
    }

    /**
     * 逻辑删除单条(§4.1.5)。@TableLogic 使 deleteById 转为 {@code UPDATE ... SET del_flag=1}，不物理删。
     * 幂等：对不存在/已删的 id 亦返回成功(受影响 0 行)。
     */
    public void delete(Long id) {
        eventRecordsMapper.deleteById(id);
        log.info("[delete] 事件逻辑删除 id={}", id);
    }

    /**
     * 批量逻辑删除(§4.1.6)。
     *
     * @return 实际置删的行数(已删/不存在的 id 不计入)
     * @throws BizException 400 ids 为空
     */
    public int batchDelete(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "ids 不能为空");
        }
        int deleted = eventRecordsMapper.deleteBatchIds(ids);
        log.info("[batchDelete] 批量逻辑删除 {} 条(请求 {} 个 id)", deleted, ids.size());
        return deleted;
    }

    /** ISO8601 带偏移时间归一化到东八区 LocalDateTime(对齐 §2.4 与 DATETIME(3) 列)。 */
    private LocalDateTime toEast8(OffsetDateTime odt) {
        return odt.withOffsetSameInstant(ZONE_EAST8).toLocalDateTime();
    }

    /** 按 deviceNum 查 camera_manage 补全设备名，未登记返回 null。 */
    private String resolveDeviceName(String deviceNum) {
        CameraManage cam = cameraManageMapper.selectOne(new LambdaQueryWrapper<CameraManage>()
                .eq(CameraManage::getDeviceNum, deviceNum)
                .last("LIMIT 1"));
        return cam == null ? null : cam.getDeviceName();
    }

    /** base64 主图转存 MinIO，返回可访问 URL；无图返回 null(Python 编码失败会传 null)。 */
    private String transferImage(String base64) {
        if (base64 == null || base64.isBlank()) {
            log.warn("[receive] snapImage 为空，跳过 MinIO 转存");
            return null;
        }
        return ossTemplate.uploadBase64(base64, SNAP_DIR, "snap.jpg", "image/jpeg");
    }

    /** 从 source_data JSON 提取 trackId；缺失或解析失败返回 null(如 people_gathering 无 trackId)。 */
    private Long extractTrackId(String sourceData) {
        if (sourceData == null || sourceData.isBlank()) {
            return null;
        }
        try {
            JSONObject obj = JSONUtil.parseObj(sourceData);
            return obj.getLong("trackId");
        } catch (Exception e) {
            log.warn("[receive] sourceData 解析 trackId 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 幂等查重：先按 device_num + snap_time(均有索引)收窄，再在 Java 侧比对 trackId。
     * 不能只用 device+snapTime——同一帧多目标(不同 trackId)会共享抓拍时间，需 trackId 区分。
     */
    private Long findDuplicate(String deviceNum, LocalDateTime snapTime, Long trackId) {
        List<EventRecords> candidates = eventRecordsMapper.selectList(new LambdaQueryWrapper<EventRecords>()
                .eq(EventRecords::getDeviceNum, deviceNum)
                .eq(EventRecords::getSnapTime, snapTime));
        for (EventRecords c : candidates) {
            if (Objects.equals(extractTrackId(c.getSourceData()), trackId)) {
                return c.getId();
            }
        }
        return null;
    }
}
