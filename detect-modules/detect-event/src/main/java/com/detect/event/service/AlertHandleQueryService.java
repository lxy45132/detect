package com.detect.event.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.detect.common.core.constant.CommonConstants;
import com.detect.common.core.domain.PageResult;
import com.detect.event.dto.HandleRecordQueryDTO;
import com.detect.event.dto.TodoQueryDTO;
import com.detect.event.entity.AlertHandleRecord;
import com.detect.event.entity.AlertRule;
import com.detect.event.entity.EventRecords;
import com.detect.event.enums.EventTypeEnum;
import com.detect.event.mapper.AlertHandleRecordMapper;
import com.detect.event.mapper.AlertRuleMapper;
import com.detect.event.mapper.EventRecordsMapper;
import com.detect.event.vo.HandleHistoryVO;
import com.detect.event.vo.HandleRecordVO;
import com.detect.event.vo.HandleStatVO;
import com.detect.event.vo.TodoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 预警处理域查询侧服务(接口文档 §4.3.1/§4.3.4/§4.3.5/§4.3.6)：待办列表、处理记录分页、事件处理历史、处理效率统计。
 *
 * <p>与写侧 {@link AlertHandleService} 分离(CQRS-lite)。统一用 {@link QueryWrapper}(字符串列名)；size 夹取 [1,200](§2.3)。
 * {@code event_records} 有 {@code @TableLogic}，selectCount/selectList 自动过滤逻辑删。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertHandleQueryService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern(CommonConstants.DATETIME_PATTERN);
    private static final long MAX_PAGE_SIZE = 200L;
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_PROCESSING = 1;
    private static final int STATUS_RESOLVED = 2;
    private static final int STATUS_IGNORED = 3;

    private final EventRecordsMapper eventRecordsMapper;
    private final AlertHandleRecordMapper alertHandleRecordMapper;
    private final AlertRuleMapper alertRuleMapper;

    /** 待办列表(§4.3.1)：{@code handle_status ∈ {0,1}}，按 {@code priority DESC, snap_time DESC}(紧急置顶)。 */
    public PageResult<TodoVO> todo(TodoQueryDTO q) {
        long current = Math.max(q.getCurrent(), 1L);
        long size = Math.min(Math.max(q.getSize(), 1L), MAX_PAGE_SIZE);

        QueryWrapper<EventRecords> w = new QueryWrapper<>();
        w.in("handle_status", STATUS_PENDING, STATUS_PROCESSING);
        w.eq(q.getPriority() != null, "priority", q.getPriority());
        w.eq(q.getEventType() != null, "event_type", q.getEventType());
        w.eq(StringUtils.hasText(q.getDeviceNum()), "device_num", q.getDeviceNum());
        w.ge(StringUtils.hasText(q.getStartTime()), "snap_time", q.getStartTime());
        w.le(StringUtils.hasText(q.getEndTime()), "snap_time", q.getEndTime());
        w.orderByDesc("priority").orderByDesc("snap_time");

        Page<EventRecords> page = eventRecordsMapper.selectPage(new Page<>(current, size), w);
        return new PageResult<>(toTodoVOs(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 处理记录分页(§4.3.4)：按 {@code handle_time DESC, id DESC}(最新处理在前)。 */
    public PageResult<HandleRecordVO> records(HandleRecordQueryDTO q) {
        long current = Math.max(q.getCurrent(), 1L);
        long size = Math.min(Math.max(q.getSize(), 1L), MAX_PAGE_SIZE);

        QueryWrapper<AlertHandleRecord> w = new QueryWrapper<>();
        w.eq(q.getEventId() != null, "event_id", q.getEventId());
        w.eq(q.getHandlerId() != null, "handler_id", q.getHandlerId());
        w.ge(StringUtils.hasText(q.getStartTime()), "handle_time", q.getStartTime());
        w.le(StringUtils.hasText(q.getEndTime()), "handle_time", q.getEndTime());
        w.orderByDesc("handle_time").orderByDesc("id");

        Page<AlertHandleRecord> page = alertHandleRecordMapper.selectPage(new Page<>(current, size), w);
        List<HandleRecordVO> vos = page.getRecords().stream().map(this::toRecordVO).toList();
        return new PageResult<>(vos, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 事件处理历史(§4.3.5)：该事件全部处理记录，时间正序；无记录/事件不存在均返回空列表。 */
    public List<HandleHistoryVO> history(Long eventId) {
        List<AlertHandleRecord> records = alertHandleRecordMapper.selectList(
                new QueryWrapper<AlertHandleRecord>().eq("event_id", eventId).orderByAsc("handle_time", "id"));
        return records.stream()
                .map(r -> new HandleHistoryVO(r.getToStatus(), r.getHandlerName(), r.getHandleRemark(),
                        formatTime(r.getHandleTime())))
                .toList();
    }

    /** 处理效率统计(§4.3.6)。公式见 {@link HandleStatVO} 类注释(规格仅给字段名，口径为本期约定)。 */
    public HandleStatVO statistics(String startTime, String endTime, String deviceNum) {
        long pending = countEvents(STATUS_PENDING, deviceNum, startTime, endTime);
        long processing = countEvents(STATUS_PROCESSING, deviceNum, startTime, endTime);
        long ignored = countEvents(STATUS_IGNORED, deviceNum, startTime, endTime);
        long total = countEvents(null, deviceNum, startTime, endTime);
        double falseRate = total == 0 ? 0.0 : ratio(ignored, total, 2);

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayResolved = alertHandleRecordMapper.selectCount(
                new QueryWrapper<AlertHandleRecord>().eq("to_status", STATUS_RESOLVED).ge("handle_time", todayStart));

        double avgHandleMinutes = avgHandleMinutes(deviceNum, startTime, endTime);
        return new HandleStatVO(pending, processing, todayResolved, falseRate, avgHandleMinutes);
    }

    // ---------------- 私有辅助 ----------------

    /** 事件计数：handleStatus 为 null 时不限状态(总数)；含 deviceNum + snap_time 区间过滤。 */
    private long countEvents(Integer handleStatus, String deviceNum, String startTime, String endTime) {
        QueryWrapper<EventRecords> w = new QueryWrapper<>();
        w.eq(handleStatus != null, "handle_status", handleStatus);
        w.eq(StringUtils.hasText(deviceNum), "device_num", deviceNum);
        w.ge(StringUtils.hasText(startTime), "snap_time", startTime);
        w.le(StringUtils.hasText(endTime), "snap_time", endTime);
        return eventRecordsMapper.selectCount(w);
    }

    /** 平均处理分钟：to_status=2 处理记录的 (handle_time − 事件 snap_time) 均值；deviceNum + handle_time 区间过滤。 */
    private double avgHandleMinutes(String deviceNum, String startTime, String endTime) {
        QueryWrapper<AlertHandleRecord> w = new QueryWrapper<>();
        w.eq("to_status", STATUS_RESOLVED);
        w.ge(StringUtils.hasText(startTime), "handle_time", startTime);
        w.le(StringUtils.hasText(endTime), "handle_time", endTime);
        List<AlertHandleRecord> resolved = alertHandleRecordMapper.selectList(w);
        if (resolved.isEmpty()) {
            return 0.0;
        }
        Set<Long> eventIds = resolved.stream().map(AlertHandleRecord::getEventId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (eventIds.isEmpty()) {
            return 0.0;
        }
        QueryWrapper<EventRecords> ew = new QueryWrapper<>();
        ew.in("id", eventIds);
        ew.eq(StringUtils.hasText(deviceNum), "device_num", deviceNum);
        Map<Long, LocalDateTime> snapTimes = eventRecordsMapper.selectList(ew).stream()
                .filter(e -> e.getSnapTime() != null)
                .collect(Collectors.toMap(EventRecords::getId, EventRecords::getSnapTime, (a, b) -> a));

        long totalMinutes = 0;
        int count = 0;
        for (AlertHandleRecord r : resolved) {
            LocalDateTime snap = snapTimes.get(r.getEventId());
            if (snap != null && r.getHandleTime() != null) {
                long mins = Duration.between(snap, r.getHandleTime()).toMinutes();
                if (mins >= 0) {
                    totalMinutes += mins;
                    count++;
                }
            }
        }
        return count == 0 ? 0.0 : ratio(totalMinutes, count, 1);
    }

    /** 批量映射 TodoVO：一次性解析 hitRuleName(去重 selectBatchIds)避免 N+1。 */
    private List<TodoVO> toTodoVOs(List<EventRecords> events) {
        Set<Long> ruleIds = events.stream().map(EventRecords::getHitRuleId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> ruleNames = ruleIds.isEmpty() ? Map.of()
                : alertRuleMapper.selectBatchIds(ruleIds).stream()
                        .collect(Collectors.toMap(AlertRule::getId, AlertRule::getRuleName, (a, b) -> a));
        return events.stream().map(e -> toTodoVO(e, ruleNames.get(e.getHitRuleId()))).toList();
    }

    private TodoVO toTodoVO(EventRecords e, String hitRuleName) {
        TodoVO vo = new TodoVO();
        vo.setId(e.getId());
        vo.setDeviceNum(e.getDeviceNum());
        vo.setDeviceName(e.getDeviceName());
        vo.setEventType(e.getEventType());
        vo.setEventTypeName(EventTypeEnum.nameOf(e.getEventType()));
        vo.setTask(extractTask(e.getSourceData()));
        vo.setSnapTime(formatTime(e.getSnapTime()));
        vo.setSnapUrl(e.getSnapUrl());
        vo.setPlateNum(e.getPlateNum());
        vo.setVehicleNormalType(e.getVehicleNormalType());
        vo.setCrowdNum(e.getCrowdNum());
        vo.setHandleStatus(e.getHandleStatus());
        vo.setPriority(e.getPriority());
        vo.setHitRuleId(e.getHitRuleId());
        vo.setHitRuleName(hitRuleName);
        return vo;
    }

    private HandleRecordVO toRecordVO(AlertHandleRecord r) {
        return new HandleRecordVO(r.getId(), r.getEventId(), r.getFromStatus(), r.getToStatus(),
                r.getHandlerName(), r.getHandleRemark(), formatTime(r.getHandleTime()));
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

    private String formatTime(LocalDateTime t) {
        return t == null ? null : t.format(DT);
    }

    /** 比率 numerator/denominator 保留 scale 位小数(denominator 为 0 时调用方须先判)。 */
    private double ratio(long numerator, long denominator, int scale) {
        double factor = Math.pow(10, scale);
        return Math.round((double) numerator / denominator * factor) / factor;
    }
}
