package com.detect.event.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.detect.common.core.domain.PageResult;
import com.detect.event.dto.HandleRecordQueryDTO;
import com.detect.event.dto.TodoQueryDTO;
import com.detect.event.entity.AlertHandleRecord;
import com.detect.event.entity.AlertRule;
import com.detect.event.entity.EventRecords;
import com.detect.event.mapper.AlertHandleRecordMapper;
import com.detect.event.mapper.AlertRuleMapper;
import com.detect.event.mapper.EventRecordsMapper;
import com.detect.event.vo.HandleHistoryVO;
import com.detect.event.vo.HandleRecordVO;
import com.detect.event.vo.HandleStatVO;
import com.detect.event.vo.TodoVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AlertHandleQueryService} 单测(6d-2)：待办列表 VO 派生 + hitRuleName 批量解析(防 N+1)、
 * 分页参数夹取、处理记录格式化、历史正序/空列表、统计五项公式(含零数据边界)。
 *
 * <p>用 Mockito 隔离三个 Mapper。{@code selectPage} 以 {@code thenAnswer} 回填分页结果；
 * {@code statistics} 内 {@code selectCount} 连续 4 次(pending/processing/ignored/total)用多值 {@code thenReturn}。
 */
@ExtendWith(MockitoExtension.class)
class AlertHandleQueryServiceTest {

    @Mock
    private EventRecordsMapper eventRecordsMapper;
    @Mock
    private AlertHandleRecordMapper alertHandleRecordMapper;
    @Mock
    private AlertRuleMapper alertRuleMapper;

    @InjectMocks
    private AlertHandleQueryService service;

    // ---------------- todo(§4.3.1) ----------------

    @Test
    void todo_mapsVoAndBatchResolvesRuleName() {
        EventRecords e1 = new EventRecords();
        e1.setId(7L);
        e1.setEventType(300);
        e1.setDeviceNum("dev01");
        e1.setDeviceName("南河湫水闸");
        e1.setSnapTime(LocalDateTime.of(2026, 9, 10, 11, 0, 0));
        e1.setCrowdNum(20);
        e1.setHandleStatus(0);
        e1.setPriority(2);
        e1.setHitRuleId(1L);
        e1.setSourceData("{\"task\":\"people_gathering\"}");
        EventRecords e2 = new EventRecords();
        e2.setId(5L);
        e2.setEventType(200);
        e2.setSnapTime(LocalDateTime.of(2026, 9, 9, 10, 0, 0));
        e2.setHandleStatus(1);
        e2.setPriority(0);
        e2.setHitRuleId(null);   // 未命中规则

        when(eventRecordsMapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<EventRecords> p = inv.getArgument(0);
            p.setRecords(List.of(e1, e2));
            p.setTotal(2L);
            return p;
        });
        AlertRule rule = new AlertRule();
        rule.setId(1L);
        rule.setRuleName("人群聚集预警");
        when(alertRuleMapper.selectBatchIds(any())).thenReturn(List.of(rule));

        PageResult<TodoVO> result = service.todo(new TodoQueryDTO());

        assertEquals(2L, result.getTotal());
        assertEquals(2, result.getRecords().size());
        TodoVO v1 = result.getRecords().get(0);
        assertEquals(7L, v1.getId());
        assertEquals("聚集", v1.getEventTypeName());
        assertEquals("people_gathering", v1.getTask());
        assertEquals("2026-09-10 11:00:00", v1.getSnapTime());
        assertEquals(20, v1.getCrowdNum().intValue());
        assertEquals(2, v1.getPriority().intValue());
        assertEquals("人群聚集预警", v1.getHitRuleName());
        TodoVO v2 = result.getRecords().get(1);
        assertEquals(5L, v2.getId());
        assertNull(v2.getHitRuleName());   // hitRuleId 为 null → 名称 null
        // 批量解析：selectBatchIds 仅调一次(避免 N+1)
        verify(alertRuleMapper, times(1)).selectBatchIds(any());
    }

    @Test
    void todo_clampsCurrentAndSize() {
        long[] captured = new long[2];
        when(eventRecordsMapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<EventRecords> p = inv.getArgument(0);
            captured[0] = p.getCurrent();
            captured[1] = p.getSize();
            p.setRecords(List.of());
            p.setTotal(0L);
            return p;
        });
        TodoQueryDTO q = new TodoQueryDTO();
        q.setCurrent(0);    // < 1 → 夹到 1
        q.setSize(500);     // > 200 → 夹到 200

        service.todo(q);

        assertEquals(1L, captured[0]);
        assertEquals(200L, captured[1]);
    }

    // ---------------- records(§4.3.4) ----------------

    @Test
    void records_mapsToVoWithFormattedTime() {
        AlertHandleRecord r = new AlertHandleRecord();
        r.setId(3L);
        r.setEventId(7L);
        r.setFromStatus(0);
        r.setToStatus(1);
        r.setHandlerName("admin");
        r.setHandleRemark("受理中");
        r.setHandleTime(LocalDateTime.of(2026, 9, 10, 12, 0, 0));
        when(alertHandleRecordMapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<AlertHandleRecord> p = inv.getArgument(0);
            p.setRecords(List.of(r));
            p.setTotal(1L);
            return p;
        });

        PageResult<HandleRecordVO> result = service.records(new HandleRecordQueryDTO());

        assertEquals(1L, result.getTotal());
        HandleRecordVO vo = result.getRecords().get(0);
        assertEquals(3L, vo.id());
        assertEquals(7L, vo.eventId());
        assertEquals(0, vo.fromStatus().intValue());
        assertEquals(1, vo.toStatus().intValue());
        assertEquals("admin", vo.handlerName());
        assertEquals("受理中", vo.handleRemark());
        assertEquals("2026-09-10 12:00:00", vo.handleTime());
    }

    // ---------------- history(§4.3.5) ----------------

    @Test
    void history_mapsRecordsInOrder() {
        AlertHandleRecord r1 = new AlertHandleRecord();
        r1.setToStatus(0);
        r1.setHandlerName("系统");
        r1.setHandleRemark("规则命中自动创建");
        r1.setHandleTime(LocalDateTime.of(2026, 9, 10, 11, 0, 1));
        AlertHandleRecord r2 = new AlertHandleRecord();
        r2.setToStatus(1);
        r2.setHandlerName("admin");
        r2.setHandleRemark("受理");
        r2.setHandleTime(LocalDateTime.of(2026, 9, 10, 12, 0, 0));
        when(alertHandleRecordMapper.selectList(any())).thenReturn(List.of(r1, r2));

        List<HandleHistoryVO> list = service.history(7L);

        assertEquals(2, list.size());
        assertEquals("系统", list.get(0).handlerName());
        assertEquals("2026-09-10 11:00:01", list.get(0).handleTime());
        assertEquals("admin", list.get(1).handlerName());
        assertEquals(1, list.get(1).toStatus().intValue());
    }

    @Test
    void history_emptyWhenNoRecords() {
        when(alertHandleRecordMapper.selectList(any())).thenReturn(List.of());

        assertTrue(service.history(999L).isEmpty());
    }

    // ---------------- statistics(§4.3.6) ----------------

    @Test
    void statistics_computesAllFormulas() {
        // countEvents 依次：pending=1, processing=0, ignored=2, total=4 → falseRate=2/4=0.5
        when(eventRecordsMapper.selectCount(any())).thenReturn(1L, 0L, 2L, 4L);
        // todayResolved：今日 to_status=2 记录数
        when(alertHandleRecordMapper.selectCount(any())).thenReturn(3L);
        // avgHandleMinutes：解决记录(eventId=7, handleTime=12:50) 对应事件 snapTime=11:00 → 110 分钟
        AlertHandleRecord r = new AlertHandleRecord();
        r.setEventId(7L);
        r.setToStatus(2);
        r.setHandleTime(LocalDateTime.of(2026, 9, 10, 12, 50, 0));
        when(alertHandleRecordMapper.selectList(any())).thenReturn(List.of(r));
        EventRecords e = new EventRecords();
        e.setId(7L);
        e.setSnapTime(LocalDateTime.of(2026, 9, 10, 11, 0, 0));
        when(eventRecordsMapper.selectList(any())).thenReturn(List.of(e));

        HandleStatVO stat = service.statistics(null, null, null);

        assertEquals(1L, stat.pendingCount());
        assertEquals(0L, stat.processingCount());
        assertEquals(3L, stat.todayResolved());
        assertEquals(0.5, stat.falseRate());
        assertEquals(110.0, stat.avgHandleMinutes());
    }

    @Test
    void statistics_noData_zeroRates() {
        when(eventRecordsMapper.selectCount(any())).thenReturn(0L, 0L, 0L, 0L);
        when(alertHandleRecordMapper.selectCount(any())).thenReturn(0L);
        when(alertHandleRecordMapper.selectList(any())).thenReturn(List.of());   // 无解决样本 → 平均 0

        HandleStatVO stat = service.statistics(null, null, null);

        assertEquals(0L, stat.pendingCount());
        assertEquals(0L, stat.processingCount());
        assertEquals(0L, stat.todayResolved());
        assertEquals(0.0, stat.falseRate());       // total=0 → 0，不除零
        assertEquals(0.0, stat.avgHandleMinutes());
    }
}
