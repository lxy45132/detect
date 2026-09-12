package com.detect.event.service;

import com.detect.event.entity.AlertHandleRecord;
import com.detect.event.entity.AlertRule;
import com.detect.event.entity.EventRecords;
import com.detect.event.mapper.AlertHandleRecordMapper;
import com.detect.event.mapper.AlertRuleMapper;
import com.detect.event.mapper.EventRecordsMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AlertMatchService} 单测(6c-2)：命中落库(hit_rule_id + priority)、系统处理记录留痕、
 * 多命中取最高优先级(平手取小 id)、事件缺失/无规则/未命中的短路。用真 {@link RuleMatcher}(@Spy)驱动匹配，
 * Mock 三个 Mapper 隔离 DB。
 */
@ExtendWith(MockitoExtension.class)
class AlertMatchServiceTest {

    @Mock
    private EventRecordsMapper eventRecordsMapper;
    @Mock
    private AlertRuleMapper alertRuleMapper;
    @Mock
    private AlertHandleRecordMapper alertHandleRecordMapper;
    @Spy
    private RuleMatcher ruleMatcher = new RuleMatcher();

    @InjectMocks
    private AlertMatchService service;

    /** 聚集事件(eventType=300, dev01, 10:00)，crowdNum 可调。 */
    private EventRecords crowdEvent(long id, int crowdNum) {
        EventRecords e = new EventRecords();
        e.setId(id);
        e.setEventType(300);
        e.setDeviceNum("dev01");
        e.setCrowdNum(crowdNum);
        e.setSnapTime(LocalDateTime.of(2026, 9, 10, 10, 0, 0));
        e.setPriority(0);
        e.setHandleStatus(0);
        return e;
    }

    /** CROWD_THRESHOLD 规则(eventType=300, enabled=1, notify=1)，阈值表达式与优先级可调。 */
    private AlertRule crowdRule(long id, String expr, int priority) {
        AlertRule r = new AlertRule();
        r.setId(id);
        r.setRuleName("人群聚集预警" + id);
        r.setRuleType("CROWD_THRESHOLD");
        r.setEventType(300);
        r.setMatchConfig("{\"crowdNum\":\"" + expr + "\"}");
        r.setPriority(priority);
        r.setEnabled(1);
        r.setNotifyEnabled(1);
        return r;
    }

    @Test
    void matchEvent_nullId_returnsFalse_noDbAccess() {
        assertFalse(service.matchEvent(null));
        verify(eventRecordsMapper, never()).selectById(any());
    }

    @Test
    void matchEvent_recordNotFound_returnsFalse_noPersist() {
        when(eventRecordsMapper.selectById(9L)).thenReturn(null);

        assertFalse(service.matchEvent(9L));

        verify(alertRuleMapper, never()).selectList(any());
        verify(eventRecordsMapper, never()).updateById(any());
        verify(alertHandleRecordMapper, never()).insert(any());
    }

    @Test
    void matchEvent_noEnabledRules_returnsFalse() {
        when(eventRecordsMapper.selectById(1L)).thenReturn(crowdEvent(1L, 12));
        when(alertRuleMapper.selectList(any())).thenReturn(List.of());

        assertFalse(service.matchEvent(1L));

        verify(eventRecordsMapper, never()).updateById(any());
        verify(alertHandleRecordMapper, never()).insert(any());
    }

    @Test
    void matchEvent_crowdHit_persistsHitRuleIdPriorityAndSystemRecord() {
        when(eventRecordsMapper.selectById(1L)).thenReturn(crowdEvent(1L, 12));
        when(alertRuleMapper.selectList(any())).thenReturn(List.of(crowdRule(50L, ">10", 2)));

        assertTrue(service.matchEvent(1L));

        // 命中落库：hit_rule_id + priority 升级
        ArgumentCaptor<EventRecords> upd = ArgumentCaptor.forClass(EventRecords.class);
        verify(eventRecordsMapper).updateById(upd.capture());
        assertEquals(1L, upd.getValue().getId());
        assertEquals(50L, upd.getValue().getHitRuleId());
        assertEquals(2, upd.getValue().getPriority().intValue());

        // 系统处理记录：handlerName=系统, fromStatus=null, toStatus=0(未处理), 备注含规则名与命中原因
        ArgumentCaptor<AlertHandleRecord> hr = ArgumentCaptor.forClass(AlertHandleRecord.class);
        verify(alertHandleRecordMapper).insert(hr.capture());
        AlertHandleRecord rec = hr.getValue();
        assertEquals(1L, rec.getEventId());
        assertNull(rec.getFromStatus());
        assertEquals(0, rec.getToStatus().intValue());
        assertEquals("系统", rec.getHandlerName());
        assertNotNull(rec.getHandleTime());
        assertTrue(rec.getHandleRemark().startsWith("规则命中："));
        assertTrue(rec.getHandleRemark().contains("crowdNum 12 命中阈值 >10"));
    }

    @Test
    void matchEvent_multiHit_picksHighestPriority() {
        when(eventRecordsMapper.selectById(1L)).thenReturn(crowdEvent(1L, 12));
        // 两条均命中(>10 与 >5)，priority 1 vs 2 → 取 priority=2 的 id=51
        when(alertRuleMapper.selectList(any())).thenReturn(List.of(
                crowdRule(50L, ">10", 1),
                crowdRule(51L, ">5", 2)));

        assertTrue(service.matchEvent(1L));

        ArgumentCaptor<EventRecords> upd = ArgumentCaptor.forClass(EventRecords.class);
        verify(eventRecordsMapper).updateById(upd.capture());
        assertEquals(51L, upd.getValue().getHitRuleId());
        assertEquals(2, upd.getValue().getPriority().intValue());
    }

    @Test
    void matchEvent_multiHit_tieBreakSmallestId() {
        when(eventRecordsMapper.selectById(1L)).thenReturn(crowdEvent(1L, 12));
        // 同 priority=1，id 50 与 51 均命中 → 取小 id=50(rules 按 id ASC 载入，严格 > 保留先到者)
        when(alertRuleMapper.selectList(any())).thenReturn(List.of(
                crowdRule(50L, ">10", 1),
                crowdRule(51L, ">5", 1)));

        assertTrue(service.matchEvent(1L));

        ArgumentCaptor<EventRecords> upd = ArgumentCaptor.forClass(EventRecords.class);
        verify(eventRecordsMapper).updateById(upd.capture());
        assertEquals(50L, upd.getValue().getHitRuleId());
    }

    @Test
    void matchEvent_belowThreshold_noHit_noPersist() {
        when(eventRecordsMapper.selectById(1L)).thenReturn(crowdEvent(1L, 5));
        when(alertRuleMapper.selectList(any())).thenReturn(List.of(crowdRule(50L, ">10", 2)));

        assertFalse(service.matchEvent(1L));

        verify(eventRecordsMapper, never()).updateById(any());
        verify(alertHandleRecordMapper, never()).insert(any());
    }
}
