package com.detect.event.service;

import com.detect.common.core.enums.ResultCode;
import com.detect.common.core.exception.BizException;
import com.detect.common.security.util.SecurityUtils;
import com.detect.event.dto.BatchHandleDTO;
import com.detect.event.dto.HandleProcessDTO;
import com.detect.event.entity.AlertHandleRecord;
import com.detect.event.entity.EventRecords;
import com.detect.event.enums.HandleStatusEnum;
import com.detect.event.mapper.AlertHandleRecordMapper;
import com.detect.event.mapper.EventRecordsMapper;
import com.detect.event.vo.BatchHandleResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 预警处理域写侧服务(接口文档 §4.3.2/§4.3.3 + §5.2 状态机)。
 *
 * <p>职责：校验状态流转合法性 → 更新 {@code event_records.handle_status} → 写 {@code alert_handle_record} 审计留痕。
 * 处理人(handlerId/handlerName)从 JWT 上下文取({@link SecurityUtils})，不接受前端传入，防越权伪造；
 * handlerName 取 JWT 的 username(令牌不含 nickname)。
 *
 * <p><b>事务边界</b>：{@link #process} 单条原子(更新+留痕同事务)；{@link #batchProcess} 整批一事务，
 * 内部<b>自调用</b> process(不经代理→不新开事务→共享本批事务)，逐条捕获 {@link BizException}
 * (校验类 1001/3001 均在写库前抛出)实现「非法跳过、合法提交」的部分成功语义(§4.3.3)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertHandleService {

    private final EventRecordsMapper eventRecordsMapper;
    private final AlertHandleRecordMapper alertHandleRecordMapper;

    /**
     * 处理单条事件(§4.3.2)：校验流转 → 更新 handle_status → 写处理记录。
     *
     * @throws BizException {@code 1001} 事件不存在(或已逻辑删，被 {@code @TableLogic} 过滤)；
     *                      {@code 3001} 状态流转非法(违反 §5.2 矩阵)
     */
    @Transactional(rollbackFor = Exception.class)
    public void process(HandleProcessDTO dto) {
        EventRecords event = eventRecordsMapper.selectById(dto.getEventId());
        if (event == null) {
            throw new BizException(ResultCode.EVENT_NOT_FOUND);
        }
        Integer from = event.getHandleStatus();
        Integer to = dto.getToStatus();
        if (!HandleStatusEnum.canTransition(from, to)) {
            throw new BizException(ResultCode.STATUS_TRANSITION_INVALID,
                    "状态流转非法：" + HandleStatusEnum.nameOf(from) + " → " + HandleStatusEnum.nameOf(to));
        }

        Long handlerId = SecurityUtils.getUserId();
        String handlerName = SecurityUtils.getUsername();
        LocalDateTime now = LocalDateTime.now();

        // 1. 更新事件处理状态(update_time 由 MetaObjectHandler 自动填充)
        EventRecords upd = new EventRecords();
        upd.setId(dto.getEventId());
        upd.setHandleStatus(to);
        eventRecordsMapper.updateById(upd);

        // 2. 写审计留痕(handle_time 显式写入——AlertHandleRecord 不继承 BaseEntity，无自动填充)
        AlertHandleRecord record = new AlertHandleRecord();
        record.setEventId(dto.getEventId());
        record.setFromStatus(from);
        record.setToStatus(to);
        record.setHandlerId(handlerId);
        record.setHandlerName(handlerName);
        record.setHandleRemark(dto.getRemark());
        record.setHandleTime(now);
        alertHandleRecordMapper.insert(record);

        log.info("[handle] 事件 {} 流转 {}→{}，处理人 {}({})", dto.getEventId(), from, to, handlerName, handlerId);
    }

    /**
     * 批量处理(§4.3.3)：逐条校验流转合法性，非法的跳过并回报，合法的提交。
     *
     * @return {@code {processed, skipped[]}}，skipped 项含 eventId 与跳过原因
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchHandleResultVO batchProcess(BatchHandleDTO dto) {
        int processed = 0;
        List<BatchHandleResultVO.Skipped> skipped = new ArrayList<>();
        for (Long eventId : dto.getEventIds()) {
            HandleProcessDTO one = new HandleProcessDTO();
            one.setEventId(eventId);
            one.setToStatus(dto.getToStatus());
            one.setRemark(dto.getRemark());
            try {
                process(one);
                processed++;
            } catch (BizException ex) {
                skipped.add(new BatchHandleResultVO.Skipped(eventId, ex.getMessage()));
                log.warn("[handle] 批量跳过事件 {}：{}", eventId, ex.getMessage());
            }
        }
        log.info("[handle] 批量处理完成：成功 {} 条，跳过 {} 条", processed, skipped.size());
        return new BatchHandleResultVO(processed, skipped);
    }
}
