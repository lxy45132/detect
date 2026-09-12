package com.detect.event.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.detect.common.core.domain.PageResult;
import com.detect.common.core.exception.BizException;
import com.detect.event.dto.AlertRuleQueryDTO;
import com.detect.event.dto.AlertRuleSaveDTO;
import com.detect.event.dto.MatchTestDTO;
import com.detect.event.entity.AlertRule;
import com.detect.event.mapper.AlertRuleMapper;
import com.detect.event.vo.AlertRuleDetailVO;
import com.detect.event.vo.AlertRuleListVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AlertRuleService} 单测(§4.2)：分页翻译、详情 JSON 解析、新增校验+默认值、增量修改、
 * 启停、逻辑删、试跑。用 Mockito 隔离 Mapper；{@link RuleMatcher} 用真实 @Spy，令 match-test 走真判定逻辑。
 */
@ExtendWith(MockitoExtension.class)
class AlertRuleServiceTest {

    @Mock
    private AlertRuleMapper alertRuleMapper;
    @Spy
    private RuleMatcher ruleMatcher = new RuleMatcher();
    @InjectMocks
    private AlertRuleService service;

    private AlertRule crowdRule() {
        AlertRule r = new AlertRule();
        r.setId(7L);
        r.setRuleName("人群聚集预警");
        r.setRuleType("CROWD_THRESHOLD");
        r.setEventType(300);
        r.setMatchConfig("{\"crowdNum\":\">10\"}");
        r.setPriority(2);
        r.setNotifyEnabled(1);
        r.setEnabled(1);
        return r;
    }

    private AlertRule vehicleRule() {
        AlertRule r = new AlertRule();
        r.setId(9L);
        r.setRuleName("渣土车布控");
        r.setRuleType("VEHICLE_TYPE");
        r.setEventType(200);
        r.setMatchConfig("{\"vehicleNormalType\":[\"SLAGTRUCK\"]}");
        r.setDeviceScope("[\"dev01\",\"dev02\"]");
        r.setTimeScope("{\"start\":\"08:00\",\"end\":\"20:00\"}");
        r.setPriority(1);
        r.setNotifyEnabled(1);
        r.setEnabled(1);
        r.setCreateBy("admin");
        r.setCreateTime(LocalDateTime.of(2026, 9, 10, 8, 0, 0));
        return r;
    }

    @Test
    void page_translatesRuleTypeAndParsesConfig() {
        when(alertRuleMapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<AlertRule> p = inv.getArgument(0);
            p.setRecords(List.of(crowdRule()));
            p.setTotal(6L);
            return p;
        });

        PageResult<AlertRuleListVO> result = service.page(new AlertRuleQueryDTO());

        assertEquals(6L, result.getTotal());
        AlertRuleListVO vo = result.getRecords().get(0);
        assertEquals("CROWD_THRESHOLD", vo.getRuleType());
        assertEquals("聚集人数阈值", vo.getRuleTypeName());
        Map<?, ?> cfg = assertInstanceOf(Map.class, vo.getMatchConfig());
        assertEquals(">10", cfg.get("crowdNum"));
    }

    @Test
    void detail_parsesJsonColumnsAndFormatsTime() {
        when(alertRuleMapper.selectById(9L)).thenReturn(vehicleRule());

        AlertRuleDetailVO vo = service.detail(9L);

        assertEquals("车辆类型", vo.getRuleTypeName());
        Map<?, ?> cfg = assertInstanceOf(Map.class, vo.getMatchConfig());
        List<?> vnt = assertInstanceOf(List.class, cfg.get("vehicleNormalType"));
        assertEquals(1, vnt.size());
        assertEquals("SLAGTRUCK", String.valueOf(vnt.get(0)));
        List<?> scope = assertInstanceOf(List.class, vo.getDeviceScope());
        assertEquals(2, scope.size());
        Map<?, ?> ts = assertInstanceOf(Map.class, vo.getTimeScope());
        assertEquals("08:00", ts.get("start"));
        assertEquals("2026-09-10 08:00:00", vo.getCreateTime());
        assertEquals("admin", vo.getCreateBy());
    }

    @Test
    void detail_notFound_throws2002() {
        when(alertRuleMapper.selectById(anyLong())).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.detail(404L));
        assertEquals(2002, ex.getCode());
    }

    @Test
    void create_appliesDefaultsAndSerializesConfig() {
        when(alertRuleMapper.insert(any())).thenAnswer(inv -> {
            AlertRule e = inv.getArgument(0);
            e.setId(11L);
            return 1;
        });
        AlertRuleSaveDTO dto = new AlertRuleSaveDTO();
        dto.setRuleName("人群聚集预警");
        dto.setRuleType("CROWD_THRESHOLD");
        dto.setEventType(300);
        dto.setMatchConfig(Map.of("crowdNum", ">10"));
        // priority/notifyEnabled/enabled 不传 -> 默认 1

        Long id = service.create(dto);

        assertEquals(11L, id);
        ArgumentCaptor<AlertRule> cap = ArgumentCaptor.forClass(AlertRule.class);
        verify(alertRuleMapper).insert(cap.capture());
        AlertRule saved = cap.getValue();
        assertEquals(1, saved.getPriority().intValue());
        assertEquals(1, saved.getNotifyEnabled().intValue());
        assertEquals(1, saved.getEnabled().intValue());
        assertTrue(saved.getMatchConfig().contains("crowdNum"));
        assertTrue(saved.getMatchConfig().contains(">10"));
    }

    @Test
    void create_invalidRuleType_throws2001() {
        AlertRuleSaveDTO dto = new AlertRuleSaveDTO();
        dto.setRuleName("x");
        dto.setRuleType("BOGUS");
        dto.setMatchConfig(Map.of("a", "b"));

        BizException ex = assertThrows(BizException.class, () -> service.create(dto));
        assertEquals(2001, ex.getCode());
        verify(alertRuleMapper, never()).insert(any());
    }

    @Test
    void create_configMismatchRuleType_throws2001() {
        AlertRuleSaveDTO dto = new AlertRuleSaveDTO();
        dto.setRuleName("x");
        dto.setRuleType("PLATE_BLACKLIST");
        dto.setMatchConfig(Map.of("crowdNum", ">10"));   // 缺 plateNum

        BizException ex = assertThrows(BizException.class, () -> service.create(dto));
        assertEquals(2001, ex.getCode());
    }

    @Test
    void create_missingRuleName_throws400() {
        AlertRuleSaveDTO dto = new AlertRuleSaveDTO();
        dto.setRuleType("CROWD_THRESHOLD");
        dto.setMatchConfig(Map.of("crowdNum", ">10"));

        BizException ex = assertThrows(BizException.class, () -> service.create(dto));
        assertEquals(400, ex.getCode());
    }

    @Test
    void update_partial_onlySetsProvidedFields() {
        when(alertRuleMapper.selectById(9L)).thenReturn(vehicleRule());
        AlertRuleSaveDTO dto = new AlertRuleSaveDTO();
        dto.setRuleName("改名渣土车");   // 仅改名

        service.update(9L, dto);

        ArgumentCaptor<AlertRule> cap = ArgumentCaptor.forClass(AlertRule.class);
        verify(alertRuleMapper).updateById(cap.capture());
        AlertRule sent = cap.getValue();
        assertEquals(9L, sent.getId());
        assertEquals("改名渣土车", sent.getRuleName());
        assertNull(sent.getRuleType());       // 未传 -> 不进 SET
        assertNull(sent.getMatchConfig());
        assertNull(sent.getPriority());
    }

    @Test
    void update_notFound_throws2002() {
        when(alertRuleMapper.selectById(anyLong())).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.update(404L, new AlertRuleSaveDTO()));
        assertEquals(2002, ex.getCode());
    }

    @Test
    void toggle_setsEnabled() {
        when(alertRuleMapper.selectById(9L)).thenReturn(vehicleRule());

        service.toggle(9L, 0);

        ArgumentCaptor<AlertRule> cap = ArgumentCaptor.forClass(AlertRule.class);
        verify(alertRuleMapper).updateById(cap.capture());
        assertEquals(0, cap.getValue().getEnabled().intValue());
    }

    @Test
    void toggle_invalidValue_throws400() {
        BizException ex = assertThrows(BizException.class, () -> service.toggle(9L, 5));
        assertEquals(400, ex.getCode());
        verify(alertRuleMapper, never()).updateById(any());
    }

    @Test
    void delete_invokesLogicDeleteById() {
        service.delete(9L);
        verify(alertRuleMapper).deleteById(9L);
    }

    @Test
    void matchTest_hit_returnsSpecReason() {
        when(alertRuleMapper.selectById(7L)).thenReturn(crowdRule());
        MatchTestDTO dto = new MatchTestDTO();
        dto.setRuleId(7L);
        MatchTestDTO.SampleEvent s = new MatchTestDTO.SampleEvent();
        s.setEventType(300);
        s.setCrowdNum(12);
        s.setDeviceNum("dev01");
        s.setSnapTime("2026-09-10 10:00:00");
        dto.setSampleEvent(s);

        MatchResult res = service.matchTest(dto);

        assertTrue(res.matched());
        assertEquals("crowdNum 12 命中阈值 >10", res.reason());
    }

    @Test
    void matchTest_ruleNotFound_throws2002() {
        when(alertRuleMapper.selectById(anyLong())).thenReturn(null);
        MatchTestDTO dto = new MatchTestDTO();
        dto.setRuleId(404L);
        dto.setSampleEvent(new MatchTestDTO.SampleEvent());

        BizException ex = assertThrows(BizException.class, () -> service.matchTest(dto));
        assertEquals(2002, ex.getCode());
    }

    @Test
    void matchTest_missingSample_throws400() {
        MatchTestDTO dto = new MatchTestDTO();
        dto.setRuleId(7L);

        BizException ex = assertThrows(BizException.class, () -> service.matchTest(dto));
        assertEquals(400, ex.getCode());
    }
}
