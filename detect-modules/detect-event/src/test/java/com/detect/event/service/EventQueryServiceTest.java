package com.detect.event.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.detect.common.core.exception.BizException;
import com.detect.event.dto.EventQueryDTO;
import com.detect.event.entity.AlertHandleRecord;
import com.detect.event.entity.AlertRule;
import com.detect.event.entity.EventRecords;
import com.detect.event.mapper.AlertHandleRecordMapper;
import com.detect.event.mapper.AlertRuleMapper;
import com.detect.event.mapper.EventRecordsMapper;
import com.detect.event.vo.EventRecordDetailVO;
import com.detect.event.vo.EventRecordListVO;
import com.detect.event.vo.EventStatVO;
import com.detect.common.core.domain.PageResult;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link EventQueryService} 单测：分页 VO 派生翻译、详情装配(sourceData/hitRule/history)、
 * 统计聚合映射、导出超限拦截。用 Mockito 隔离 Mapper，不依赖真实 DB。
 */
@ExtendWith(MockitoExtension.class)
class EventQueryServiceTest {

    @Mock
    private EventRecordsMapper eventRecordsMapper;
    @Mock
    private AlertRuleMapper alertRuleMapper;
    @Mock
    private AlertHandleRecordMapper alertHandleRecordMapper;

    @InjectMocks
    private EventQueryService service;

    private EventRecords sampleEntity() {
        EventRecords e = new EventRecords();
        e.setId(10086L);
        e.setDeviceNum("dev01");
        e.setDeviceName("南河湫水闸");
        e.setEventType(200);
        e.setSnapTime(LocalDateTime.of(2026, 9, 10, 8, 49, 50, 123_000_000));
        e.setSourceData("{\"task\":\"vehicle_type\",\"confidence\":0.87,\"trackId\":12,\"bbox\":[100.0,200.0,300.0,400.0]}");
        e.setVehicleNormalType("SLAGTRUCK");
        e.setHandleStatus(0);
        e.setPriority(1);
        e.setHitRuleId(5L);
        return e;
    }

    @Test
    void page_mapsEntityToVoWithDerivedNames() {
        when(eventRecordsMapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<EventRecords> p = inv.getArgument(0);
            p.setRecords(List.of(sampleEntity()));
            p.setTotal(128L);
            return p;
        });

        PageResult<EventRecordListVO> result = service.page(new EventQueryDTO());

        assertEquals(128L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        EventRecordListVO vo = result.getRecords().get(0);
        assertEquals(10086L, vo.getId());
        assertEquals("车辆", vo.getEventTypeName());
        assertEquals("vehicle_type", vo.getTask());
        assertEquals("2026-09-10 08:49:50", vo.getSnapTime());
        assertEquals("SLAGTRUCK", vo.getVehicleNormalType());
        assertEquals(1, vo.getPriority().intValue());
        assertEquals(5L, vo.getHitRuleId());
    }

    @Test
    void detail_assemblesSourceDataHitRuleAndHistory() {
        when(eventRecordsMapper.selectById(10086L)).thenReturn(sampleEntity());
        AlertRule rule = new AlertRule();
        rule.setId(5L);
        rule.setRuleName("渣土车布控");
        rule.setRuleType("VEHICLE_TYPE");
        when(alertRuleMapper.selectById(5L)).thenReturn(rule);
        AlertHandleRecord rec = new AlertHandleRecord();
        rec.setToStatus(0);
        rec.setHandlerName("系统");
        rec.setHandleRemark("规则命中自动创建");
        rec.setHandleTime(LocalDateTime.of(2026, 9, 10, 8, 49, 51));
        when(alertHandleRecordMapper.selectList(any())).thenReturn(List.of(rec));

        EventRecordDetailVO vo = service.detail(10086L);

        assertEquals("车辆", vo.getEventTypeName());
        assertEquals("2026-09-10 08:49:50", vo.getSnapTime());
        // sourceData 解析为 Map，可取 task
        Map<?, ?> src = assertInstanceOf(Map.class, vo.getSourceData());
        assertEquals("vehicle_type", src.get("task"));
        // 命中规则摘要
        assertEquals(5L, vo.getHitRule().id());
        assertEquals("渣土车布控", vo.getHitRule().ruleName());
        assertEquals("VEHICLE_TYPE", vo.getHitRule().ruleType());
        // 处理历史
        assertEquals(1, vo.getHandleHistory().size());
        assertEquals("系统", vo.getHandleHistory().get(0).handlerName());
        assertEquals("2026-09-10 08:49:51", vo.getHandleHistory().get(0).handleTime());
    }

    @Test
    void detail_notFound_throws1001() {
        when(eventRecordsMapper.selectById(anyLong())).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.detail(999L));
        assertEquals(1001, ex.getCode());
    }

    @Test
    void statistics_mapsAggregatesWithNames() {
        when(eventRecordsMapper.selectCount(any())).thenReturn(1280L);
        List<Map<String, Object>> byEventType = List.of(Map.<String, Object>of("code", 200, "count", 900L));
        List<Map<String, Object>> byTask = List.of(Map.<String, Object>of("task", "license_plate", "count", 500L));
        List<Map<String, Object>> byDay = List.of(Map.<String, Object>of("date", LocalDate.of(2026, 9, 8), "count", 600L));
        when(eventRecordsMapper.selectMaps(any())).thenReturn(byEventType, byTask, byDay);

        EventStatVO stat = service.statistics(null, null, null);

        assertEquals(1280L, stat.total());
        assertEquals(1, stat.byEventType().size());
        assertEquals(200, stat.byEventType().get(0).code().intValue());
        assertEquals("车辆", stat.byEventType().get(0).name());
        assertEquals(900L, stat.byEventType().get(0).count());
        assertEquals("license_plate", stat.byTask().get(0).task());
        assertEquals("车牌识别", stat.byTask().get(0).name());
        assertEquals(500L, stat.byTask().get(0).count());
        assertEquals("2026-09-08", stat.byDay().get(0).date());
        assertEquals(600L, stat.byDay().get(0).count());
    }

    @Test
    void export_exceedsLimit_throws4001WithoutTouchingResponse() {
        when(eventRecordsMapper.selectCount(any())).thenReturn(50_001L);
        HttpServletResponse response = mock(HttpServletResponse.class);

        BizException ex = assertThrows(BizException.class,
                () -> service.export(new EventQueryDTO(), "xlsx", response));

        assertEquals(4001, ex.getCode());
        verify(eventRecordsMapper, never()).selectList(any());
    }
}
