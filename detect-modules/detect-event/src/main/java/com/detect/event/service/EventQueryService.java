package com.detect.event.service;

import cn.hutool.json.JSONUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.detect.common.core.constant.CommonConstants;
import com.detect.common.core.domain.PageResult;
import com.detect.common.core.enums.ResultCode;
import com.detect.common.core.exception.BizException;
import com.detect.common.oss.OssTemplate;
import com.detect.event.dto.EventQueryDTO;
import com.detect.event.entity.AlertHandleRecord;
import com.detect.event.entity.AlertRule;
import com.detect.event.entity.EventRecords;
import com.detect.event.enums.EventTypeEnum;
import com.detect.event.enums.HandleStatusEnum;
import com.detect.event.enums.PriorityEnum;
import com.detect.event.enums.TaskTypeEnum;
import com.detect.event.mapper.AlertHandleRecordMapper;
import com.detect.event.mapper.AlertRuleMapper;
import com.detect.event.mapper.EventRecordsMapper;
import com.detect.event.vo.EventExportRow;
import com.detect.event.vo.EventRecordDetailVO;
import com.detect.event.vo.EventRecordListVO;
import com.detect.event.vo.EventStatVO;
import com.detect.event.vo.HandleHistoryVO;
import com.detect.event.vo.HitRuleVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 事件查询侧服务(接口文档 §4.1.2/4.1.3/4.1.7/4.1.8)：分页、详情、分类统计、导出。
 *
 * <p>与写侧 {@link EventRecordService}(receive/update/delete) 分离，符合 CQRS-lite。
 * 统一用 {@link QueryWrapper}(字符串列名)以支持 {@code source_data} 的 MySQL JSON 函数过滤与聚合投影。
 *
 * <p><b>COUNT 防坑</b>：手工 {@code selectCount} 用「仅过滤」wrapper(不含 ORDER BY)——
 * {@code SELECT COUNT(*) ... ORDER BY snap_time} 在 {@code ONLY_FULL_GROUP_BY} 下非法；
 * 分页 {@code selectPage} 的内置 count 由 MyBatis-Plus 自动剥离 ORDER BY，不受影响。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventQueryService {

    /** 时间输出格式(§2.4 东八区) */
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern(CommonConstants.DATETIME_PATTERN);
    /** 导出文件名时间戳 */
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** 同步导出单次上限(§4.1.8)，超限报 4001 */
    private static final long EXPORT_LIMIT = 50_000L;
    /** 每页条数上限(§2.3) */
    private static final long MAX_PAGE_SIZE = 200L;
    /** source_data 中提取 task 的 JSON 路径表达式 */
    private static final String TASK_JSON = "JSON_UNQUOTE(JSON_EXTRACT(source_data, '$.task'))";
    /** source_data 解析用 Jackson mapper：产出标准 Map/List(JSON null→Java null)，规避 hutool JSONNull 无 Jackson 序列化器 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final EventRecordsMapper eventRecordsMapper;
    private final AlertRuleMapper alertRuleMapper;
    private final AlertHandleRecordMapper alertHandleRecordMapper;
    /** 只用于读出时把库里的稳定图片 URL 换成限时预签名 URL（桶保持私有） */
    private final OssTemplate ossTemplate;

    /**
     * 分页查询事件列表(§4.1.2)。默认 {@code snap_time DESC}；size 夹取 [1,200]。
     * 列表项翻译 {@code eventTypeName}、从 source_data 提取 {@code task}、格式化 {@code snapTime}。
     */
    public PageResult<EventRecordListVO> page(EventQueryDTO q) {
        long current = Math.max(q.getCurrent(), 1L);
        long size = Math.min(Math.max(q.getSize(), 1L), MAX_PAGE_SIZE);

        QueryWrapper<EventRecords> w = new QueryWrapper<>();
        applyFilters(w, q);
        w.orderByDesc("snap_time");

        Page<EventRecords> page = eventRecordsMapper.selectPage(new Page<>(current, size), w);
        List<EventRecordListVO> vos = page.getRecords().stream().map(this::toListVO).toList();
        return new PageResult<>(vos, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 事件详情(§4.1.3)：完整字段 + sourceData 解析对象 + 命中规则摘要 + 处理历史(时间升序)。
     *
     * @throws BizException 1001 事件不存在(或已逻辑删除，被 @TableLogic 过滤)
     */
    public EventRecordDetailVO detail(Long id) {
        EventRecords e = eventRecordsMapper.selectById(id);
        if (e == null) {
            throw new BizException(ResultCode.EVENT_NOT_FOUND);
        }
        EventRecordDetailVO vo = new EventRecordDetailVO();
        vo.setId(e.getId());
        vo.setDeviceNum(e.getDeviceNum());
        vo.setDeviceName(e.getDeviceName());
        vo.setEventType(e.getEventType());
        vo.setEventTypeName(EventTypeEnum.nameOf(e.getEventType()));
        vo.setSnapTime(formatTime(e.getSnapTime()));
        // 桶保持私有：库里存的是稳定 URL，匿名请求会被 MinIO 拒 403；
        // 读出时换成限时预签名 URL，浏览器 <img> 无需任何凭据即可取图。
        // 字段为 null 时原样返回 null（前端据此显示「无抓拍图」）。
        vo.setSnapUrl(ossTemplate.toPresignedUrl(e.getSnapUrl()));
        vo.setName(e.getName());
        vo.setCardno(e.getCardno());
        vo.setLibName(e.getLibName());
        vo.setSimilarity(e.getSimilarity());
        // 人脸库两张图同为 MinIO 对象，同样需签名；若存的是供应商外链则反解失败、原样返回
        vo.setIdentifyFaceUrl(ossTemplate.toPresignedUrl(e.getIdentifyFaceUrl()));
        vo.setVisibleLightUrl(ossTemplate.toPresignedUrl(e.getVisibleLightUrl()));
        vo.setPlateNum(e.getPlateNum());
        vo.setVehicleType(e.getVehicleType());
        vo.setVehicleNormalType(e.getVehicleNormalType());
        vo.setVehicleLogo(e.getVehicleLogo());
        vo.setVehicleSubLogo(e.getVehicleSubLogo());
        vo.setVehicleColor(e.getVehicleColor());
        vo.setVehicleModel(e.getVehicleModel());
        vo.setHeightPermitted(e.getHeightPermitted());
        vo.setCrowdNum(e.getCrowdNum());
        vo.setStatus(e.getStatus());
        vo.setHandleStatus(e.getHandleStatus());
        vo.setPriority(e.getPriority());
        vo.setHitRuleId(e.getHitRuleId());
        vo.setSourceData(parseSourceData(e.getSourceData()));
        vo.setHitRule(loadHitRule(e.getHitRuleId()));
        vo.setHandleHistory(loadHandleHistory(id));
        return vo;
    }

    /**
     * 分类统计(§4.1.7)：一次性返回 total + 按大类/子类/日期聚合，供看板使用。
     * 仅接受 startTime/endTime/deviceNum 三个过滤条件。
     */
    public EventStatVO statistics(String startTime, String endTime, String deviceNum) {
        long total = eventRecordsMapper.selectCount(statWrapper(deviceNum, startTime, endTime));

        QueryWrapper<EventRecords> ew = statWrapper(deviceNum, startTime, endTime);
        ew.select("event_type AS code", "COUNT(*) AS `count`");
        ew.groupBy("event_type");
        ew.orderByDesc("COUNT(*)");
        List<EventStatVO.EventTypeCount> byEventType = eventRecordsMapper.selectMaps(ew).stream()
                .map(m -> {
                    Integer code = toInt(m.get("code"));
                    return new EventStatVO.EventTypeCount(code, EventTypeEnum.nameOf(code), toLong(m.get("count")));
                })
                .toList();

        QueryWrapper<EventRecords> tw = statWrapper(deviceNum, startTime, endTime);
        tw.select(TASK_JSON + " AS task", "COUNT(*) AS `count`");
        tw.groupBy(TASK_JSON);
        tw.orderByDesc("COUNT(*)");
        List<EventStatVO.TaskCount> byTask = eventRecordsMapper.selectMaps(tw).stream()
                .map(m -> {
                    String task = (String) m.get("task");
                    return new EventStatVO.TaskCount(task, TaskTypeEnum.nameOf(task), toLong(m.get("count")));
                })
                .toList();

        QueryWrapper<EventRecords> dw = statWrapper(deviceNum, startTime, endTime);
        dw.select("DATE(snap_time) AS `date`", "COUNT(*) AS `count`");
        dw.groupBy("DATE(snap_time)");
        dw.orderByAsc("DATE(snap_time)");
        List<EventStatVO.DayCount> byDay = eventRecordsMapper.selectMaps(dw).stream()
                .map(m -> new EventStatVO.DayCount(toDateStr(m.get("date")), toLong(m.get("count"))))
                .toList();

        return new EventStatVO(total, byEventType, byTask, byDay);
    }

    /**
     * 导出(§4.1.8)：命中筛选的全部事件(不分页)写为 xlsx/csv 文件流。
     * 先计数校验上限(>5 万抛 4001，此时尚未写响应头，异常处理器可正常回 JSON)，再设头 + EasyExcel 写出。
     *
     * @param format {@code csv} 走 CSV，其余(含 null)走 xlsx
     */
    public void export(EventQueryDTO dto, String format, HttpServletResponse response) {
        QueryWrapper<EventRecords> countW = new QueryWrapper<>();
        applyFilters(countW, dto);
        long count = eventRecordsMapper.selectCount(countW);
        if (count > EXPORT_LIMIT) {
            throw new BizException(ResultCode.EXPORT_LIMIT_EXCEEDED,
                    "导出数量 " + count + " 超过上限 " + EXPORT_LIMIT + "，请缩小时间范围");
        }

        QueryWrapper<EventRecords> listW = new QueryWrapper<>();
        applyFilters(listW, dto);
        listW.orderByDesc("snap_time");
        List<EventExportRow> rows = eventRecordsMapper.selectList(listW).stream().map(this::toExportRow).toList();

        boolean csv = "csv".equalsIgnoreCase(format);
        ExcelTypeEnum type = csv ? ExcelTypeEnum.CSV : ExcelTypeEnum.XLSX;
        String ext = csv ? ".csv" : ".xlsx";
        String filename = "event_" + LocalDateTime.now().format(FILE_STAMP);
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

        response.setContentType(csv ? "text/csv;charset=utf-8" : "application/vnd.ms-excel;charset=utf-8");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition",
                "attachment;filename=" + encoded + ext + ";filename*=utf-8''" + encoded + ext);
        try {
            EasyExcel.write(response.getOutputStream(), EventExportRow.class)
                    .excelType(type)
                    .sheet("事件记录")
                    .doWrite(rows);
        } catch (IOException ex) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "导出失败: " + ex.getMessage());
        }
        log.info("[export] 导出 {} 条事件，格式 {}", rows.size(), ext);
    }

    // ---------------- 私有辅助 ----------------

    /** 事件列表/导出的完整筛选(WHERE，不含 select/orderBy)。 */
    private void applyFilters(QueryWrapper<EventRecords> w, EventQueryDTO q) {
        applyCommonFilters(w, q.getDeviceNum(), q.getStartTime(), q.getEndTime());
        w.eq(q.getEventType() != null, "event_type", q.getEventType());
        w.eq(q.getHandleStatus() != null, "handle_status", q.getHandleStatus());
        w.eq(q.getPriority() != null, "priority", q.getPriority());
        w.like(StringUtils.hasText(q.getPlateNum()), "plate_num", q.getPlateNum());
        // task 存于 source_data JSON 内，用 MySQL JSON 函数参数化过滤({0} 占位防注入)
        w.apply(StringUtils.hasText(q.getTask()), TASK_JSON + " = {0}", q.getTask());
        // keyword：车牌 OR 设备名，用 and(...) 包裹避免 OR 破坏其他条件的 AND 语义
        w.and(StringUtils.hasText(q.getKeyword()),
                k -> k.like("plate_num", q.getKeyword()).or().like("device_name", q.getKeyword()));
    }

    /** 统计共用的时间/设备过滤(§4.1.7 仅此三项)。 */
    private void applyCommonFilters(QueryWrapper<EventRecords> w, String deviceNum, String startTime, String endTime) {
        w.eq(StringUtils.hasText(deviceNum), "device_num", deviceNum);
        w.ge(StringUtils.hasText(startTime), "snap_time", startTime);
        w.le(StringUtils.hasText(endTime), "snap_time", endTime);
    }

    /** 统计用「仅过滤」wrapper(不含 orderBy，供 selectCount/selectMaps 安全复用)。 */
    private QueryWrapper<EventRecords> statWrapper(String deviceNum, String startTime, String endTime) {
        QueryWrapper<EventRecords> w = new QueryWrapper<>();
        applyCommonFilters(w, deviceNum, startTime, endTime);
        return w;
    }

    private EventRecordListVO toListVO(EventRecords e) {
        EventRecordListVO vo = new EventRecordListVO();
        vo.setId(e.getId());
        vo.setDeviceNum(e.getDeviceNum());
        vo.setDeviceName(e.getDeviceName());
        vo.setEventType(e.getEventType());
        vo.setEventTypeName(EventTypeEnum.nameOf(e.getEventType()));
        vo.setTask(extractTask(e.getSourceData()));
        vo.setSnapTime(formatTime(e.getSnapTime()));
        // 同详情：列表缩略图也需预签名，否则私有桶下全部 403
        vo.setSnapUrl(ossTemplate.toPresignedUrl(e.getSnapUrl()));
        vo.setPlateNum(e.getPlateNum());
        vo.setVehicleNormalType(e.getVehicleNormalType());
        vo.setCrowdNum(e.getCrowdNum());
        vo.setHandleStatus(e.getHandleStatus());
        vo.setPriority(e.getPriority());
        vo.setHitRuleId(e.getHitRuleId());
        vo.setAiCorrected(extractAiCorrected(e.getSourceData()));
        return vo;
    }

    private EventExportRow toExportRow(EventRecords e) {
        EventExportRow r = new EventExportRow();
        r.setId(e.getId());
        r.setDeviceNum(e.getDeviceNum());
        r.setDeviceName(e.getDeviceName());
        r.setEventTypeName(EventTypeEnum.nameOf(e.getEventType()));
        r.setTask(extractTask(e.getSourceData()));
        r.setSnapTime(formatTime(e.getSnapTime()));
        r.setPlateNum(e.getPlateNum());
        r.setVehicleNormalType(e.getVehicleNormalType());
        r.setCrowdNum(e.getCrowdNum());
        r.setHandleStatusName(HandleStatusEnum.nameOf(e.getHandleStatus()));
        r.setPriorityName(PriorityEnum.nameOf(e.getPriority()));
        r.setHitRuleId(e.getHitRuleId());
        return r;
    }

    private HitRuleVO loadHitRule(Long hitRuleId) {
        if (hitRuleId == null) {
            return null;
        }
        AlertRule rule = alertRuleMapper.selectById(hitRuleId);
        return rule == null ? null : new HitRuleVO(rule.getId(), rule.getRuleName(), rule.getRuleType());
    }

    private List<HandleHistoryVO> loadHandleHistory(Long eventId) {
        List<AlertHandleRecord> records = alertHandleRecordMapper.selectList(
                new QueryWrapper<AlertHandleRecord>().eq("event_id", eventId).orderByAsc("handle_time", "id"));
        return records.stream()
                .map(r -> new HandleHistoryVO(r.getToStatus(), r.getHandlerName(), r.getHandleRemark(),
                        formatTime(r.getHandleTime())))
                .toList();
    }

    /**
     * source_data JSON 字符串解析为标准对象(Map/List/标量)供 Jackson 直出；失败回退原始字符串。
     * 用 Jackson(而非 hutool JSONUtil)解析：JSON null 映射为 Java null，避免 hutool 的
     * {@code cn.hutool.json.JSONNull} 单例无 Jackson 序列化器导致详情响应 500。
     */
    private Object parseSourceData(String sourceData) {
        if (!StringUtils.hasText(sourceData)) {
            return null;
        }
        try {
            return MAPPER.readValue(sourceData, Object.class);
        } catch (Exception ex) {
            log.warn("[detail] sourceData 解析失败，回退原始字符串: {}", ex.getMessage());
            return sourceData;
        }
    }

    /** 从 source_data 提取 task；缺失或解析失败返回 null。 */
    private String extractTask(String sourceData) {
        if (!StringUtils.hasText(sourceData)) {
            return null;
        }
        try {
            return JSONUtil.parseObj(sourceData).getStr("task");
        } catch (Exception ex) {
            return null;
        }
    }

    /** 从 source_data 提取 aiReview.overridden；缺失或解析失败返回 false。 */
    private Boolean extractAiCorrected(String sourceData) {
        if (!StringUtils.hasText(sourceData)) {
            return false;
        }
        try {
            var obj = JSONUtil.parseObj(sourceData);
            var aiReview = obj.getJSONObject("aiReview");
            if (aiReview == null) {
                return false;
            }
            return Boolean.TRUE.equals(aiReview.getBool("overridden", false));
        } catch (Exception ex) {
            log.debug("[extractAiCorrected] sourceData 解析失败，降级 false: {}", ex.getMessage());
            return false;
        }
    }

    private String formatTime(LocalDateTime t) {
        return t == null ? null : t.format(DT);
    }

    private Integer toInt(Object o) {
        return o == null ? null : ((Number) o).intValue();
    }

    private Long toLong(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    /** DATE() 结果(java.sql.Date / LocalDate)统一转 yyyy-MM-dd 字符串。 */
    private String toDateStr(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o);
        return s.length() >= 10 ? s.substring(0, 10) : s;
    }
}
