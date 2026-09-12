package com.detect.event.service;

import com.detect.event.entity.AlertRule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RuleMatcher} 纯单元测试(无 Spring / 无 mock)：覆盖 4 类 ruleType 核心匹配 + 3 道门控
 * (event_type / device_scope / time_scope) + crowdNum 阈值表达式解析。锁定 §4.2.7 命中原因文案。
 */
class RuleMatcherTest {

    private final RuleMatcher matcher = new RuleMatcher();

    private AlertRule rule(String type, Integer eventType, String matchConfig) {
        AlertRule r = new AlertRule();
        r.setRuleType(type);
        r.setEventType(eventType);
        r.setMatchConfig(matchConfig);
        return r;
    }

    private RuleMatchInput in(Integer eventType, String deviceNum, String plateNum,
                              String vehicleNormalType, Integer crowdNum, LocalDateTime snapTime) {
        return new RuleMatchInput(eventType, deviceNum, plateNum, null, vehicleNormalType, crowdNum, snapTime);
    }

    @Test
    void crowdThreshold_hit_reasonMatchesSpec() {
        AlertRule r = rule("CROWD_THRESHOLD", 300, "{\"crowdNum\":\">10\"}");
        MatchResult res = matcher.match(r, in(300, "dev01", null, null, 12,
                LocalDateTime.of(2026, 9, 10, 10, 0, 0)));
        assertTrue(res.matched());
        assertEquals("crowdNum 12 命中阈值 >10", res.reason());
    }

    @Test
    void crowdThreshold_miss_whenBelow() {
        AlertRule r = rule("CROWD_THRESHOLD", 300, "{\"crowdNum\":\">10\"}");
        assertFalse(matcher.match(r, in(300, "dev01", null, null, 8, null)).matched());
    }

    @Test
    void crowdThreshold_supportsAllOperators() {
        assertTrue(matcher.match(rule("CROWD_THRESHOLD", null, "{\"crowdNum\":\">=5\"}"),
                in(300, null, null, null, 5, null)).matched());
        assertTrue(matcher.match(rule("CROWD_THRESHOLD", null, "{\"crowdNum\":\"<3\"}"),
                in(300, null, null, null, 2, null)).matched());
        assertTrue(matcher.match(rule("CROWD_THRESHOLD", null, "{\"crowdNum\":\"=8\"}"),
                in(300, null, null, null, 8, null)).matched());
        assertFalse(matcher.match(rule("CROWD_THRESHOLD", null, "{\"crowdNum\":\">10\"}"),
                in(300, null, null, null, 10, null)).matched());
    }

    @Test
    void crowdThreshold_invalidExpr_miss() {
        AlertRule r = rule("CROWD_THRESHOLD", 300, "{\"crowdNum\":\"abc\"}");
        MatchResult res = matcher.match(r, in(300, null, null, null, 12, null));
        assertFalse(res.matched());
        assertTrue(res.reason().contains("表达式非法"));
    }

    @Test
    void eventTypeGate_blocksBeforeCore() {
        // 规则限定 300，事件为 200：即便 crowdNum 满足也应 miss(大类门控先行)
        AlertRule r = rule("CROWD_THRESHOLD", 300, "{\"crowdNum\":\">10\"}");
        MatchResult res = matcher.match(r, in(200, "dev01", null, null, 99, null));
        assertFalse(res.matched());
        assertTrue(res.reason().contains("事件大类不匹配"));
    }

    @Test
    void plateBlacklist_hit_and_miss() {
        AlertRule r = rule("PLATE_BLACKLIST", 200, "{\"plateNum\":[\"京A12345\",\"沪B67890\"]}");
        assertTrue(matcher.match(r, in(200, "dev01", "京A12345", null, null, null)).matched());
        assertFalse(matcher.match(r, in(200, "dev01", "粤C111", null, null, null)).matched());
    }

    @Test
    void vehicleType_hit() {
        AlertRule r = rule("VEHICLE_TYPE", 200, "{\"vehicleNormalType\":[\"SLAGTRUCK\"]}");
        MatchResult res = matcher.match(r, in(200, "dev01", null, "SLAGTRUCK", null, null));
        assertTrue(res.matched());
        assertTrue(res.reason().contains("命中布控"));
    }

    @Test
    void deviceScope_gate_miss() {
        AlertRule r = rule("VEHICLE_TYPE", null, "{\"vehicleNormalType\":[\"SLAGTRUCK\"]}");
        r.setDeviceScope("[\"dev02\"]");
        // 设备 dev01 不在 device_scope，即便车型命中也 miss
        MatchResult res = matcher.match(r, in(200, "dev01", null, "SLAGTRUCK", null, null));
        assertFalse(res.matched());
        assertTrue(res.reason().contains("不在生效范围"));
    }

    @Test
    void deviceTime_hit_withinWrappedTimeScope() {
        AlertRule r = rule("DEVICE_TIME", null, "{\"deviceNum\":[\"dev01\"]}");
        r.setTimeScope("{\"start\":\"22:00\",\"end\":\"06:00\"}");   // 跨零点
        MatchResult res = matcher.match(r, in(200, "dev01", null, null, null,
                LocalDateTime.of(2026, 9, 10, 23, 30, 0)));
        assertTrue(res.matched());
    }

    @Test
    void deviceTime_miss_outsideTimeScope() {
        AlertRule r = rule("DEVICE_TIME", null, "{\"deviceNum\":[\"dev01\"]}");
        r.setTimeScope("{\"start\":\"22:00\",\"end\":\"06:00\"}");
        MatchResult res = matcher.match(r, in(200, "dev01", null, null, null,
                LocalDateTime.of(2026, 9, 10, 12, 0, 0)));
        assertFalse(res.matched());
        assertTrue(res.reason().contains("不在生效时段"));
    }

    @Test
    void timeScope_sameDayWindow() {
        AlertRule r = rule("DEVICE_TIME", null, "{\"deviceNum\":[\"dev01\"]}");
        r.setTimeScope("{\"start\":\"08:00\",\"end\":\"20:00\"}");   // 同日窗口
        assertTrue(matcher.match(r, in(200, "dev01", null, null, null,
                LocalDateTime.of(2026, 9, 10, 10, 0, 0))).matched());
        assertFalse(matcher.match(r, in(200, "dev01", null, null, null,
                LocalDateTime.of(2026, 9, 10, 21, 0, 0))).matched());
    }

    @Test
    void unknownRuleType_miss() {
        AlertRule r = rule("SOMETHING_ELSE", null, "{}");
        assertFalse(matcher.match(r, in(200, "dev01", null, null, null, null)).matched());
    }
}
