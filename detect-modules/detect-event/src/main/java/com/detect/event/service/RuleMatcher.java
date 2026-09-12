package com.detect.event.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.detect.event.entity.AlertRule;
import com.detect.event.enums.RuleTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 布控规则匹配引擎(§3.1 ruleType + §4.2.7 试跑)。纯函数式：给定一条规则与一个事件样本，
 * 返回是否命中及原因，<b>不落库、无副作用</b>。6c-1 供 match-test 调试，6c-2 供队列消费者复用。
 *
 * <p>判定顺序(任一门控不通过即 miss)：
 * <ol>
 *   <li>事件大类 event_type：规则限定非空则须与事件相等</li>
 *   <li>设备范围 device_scope：JSON 数组非空则 device_num 须在其中</li>
 *   <li>生效时段 time_scope：{start,end} 日内窗口，支持跨零点；snapTime 或 time_scope 缺失则放行</li>
 *   <li>按 rule_type 解析 match_config 做核心匹配</li>
 * </ol>
 *
 * <p><b>不校验 rule.enabled</b>——停用规则仍可试跑；是否加载停用规则由调用方(消费者)决定。
 */
@Slf4j
@Component
public class RuleMatcher {

    /** crowdNum 阈值表达式：如 ">10"、">=5"、"<3"、"=8"。 */
    private static final Pattern CROWD_EXPR = Pattern.compile("^\\s*(>=|<=|==|>|<|=)\\s*(-?\\d+)\\s*$");
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    public MatchResult match(AlertRule rule, RuleMatchInput in) {
        if (rule == null || in == null) {
            return MatchResult.miss("规则或事件样本为空");
        }
        // 1. 事件大类门控
        if (rule.getEventType() != null && !rule.getEventType().equals(in.eventType())) {
            return MatchResult.miss("事件大类不匹配：规则限定 " + rule.getEventType() + "，事件为 " + in.eventType());
        }
        // 2. 设备范围门控
        List<String> deviceScope = parseStringArray(rule.getDeviceScope());
        if (deviceScope != null && !deviceScope.isEmpty() && !deviceScope.contains(in.deviceNum())) {
            return MatchResult.miss("设备 " + in.deviceNum() + " 不在生效范围 " + deviceScope);
        }
        // 3. 生效时段门控(snapTime 或 time_scope 缺失则放行)
        JSONObject timeScope = parseObj(rule.getTimeScope());
        if (timeScope != null && in.snapTime() != null && !inTimeScope(timeScope, in.snapTime().toLocalTime())) {
            return MatchResult.miss("抓拍时间 " + in.snapTime().toLocalTime() + " 不在生效时段 " + rule.getTimeScope());
        }
        // 4. 核心匹配
        String ruleType = rule.getRuleType();
        if (!RuleTypeEnum.isValid(ruleType)) {
            return MatchResult.miss("未知规则类型：" + ruleType);
        }
        JSONObject cfg = parseObj(rule.getMatchConfig());
        if (cfg == null) {
            return MatchResult.miss("match_config 为空或非法 JSON");
        }
        return switch (RuleTypeEnum.valueOf(ruleType)) {
            case PLATE_BLACKLIST -> matchPlateBlacklist(cfg, in);
            case VEHICLE_TYPE -> matchVehicleType(cfg, in);
            case CROWD_THRESHOLD -> matchCrowdThreshold(cfg, in);
            case DEVICE_TIME -> matchDeviceTime(cfg, in);
        };
    }

    private MatchResult matchPlateBlacklist(JSONObject cfg, RuleMatchInput in) {
        if (in.plateNum() == null || in.plateNum().isBlank()) {
            return MatchResult.miss("事件无车牌，无法命中黑名单");
        }
        List<String> plates = stringList(cfg.get("plateNum"));
        return plates.contains(in.plateNum())
                ? MatchResult.hit("车牌 " + in.plateNum() + " 命中黑名单")
                : MatchResult.miss("车牌 " + in.plateNum() + " 未命中黑名单");
    }

    private MatchResult matchVehicleType(JSONObject cfg, RuleMatchInput in) {
        if (in.vehicleNormalType() == null || in.vehicleNormalType().isBlank()) {
            return MatchResult.miss("事件无车辆类型，无法命中布控");
        }
        List<String> types = stringList(cfg.get("vehicleNormalType"));
        return types.contains(in.vehicleNormalType())
                ? MatchResult.hit("车辆类型 " + in.vehicleNormalType() + " 命中布控")
                : MatchResult.miss("车辆类型 " + in.vehicleNormalType() + " 未命中布控");
    }

    private MatchResult matchCrowdThreshold(JSONObject cfg, RuleMatchInput in) {
        String expr = cfg.getStr("crowdNum");
        if (expr == null) {
            return MatchResult.miss("match_config 缺少 crowdNum 阈值");
        }
        if (in.crowdNum() == null) {
            return MatchResult.miss("事件无聚集人数，无法比较阈值 " + expr.trim());
        }
        Matcher m = CROWD_EXPR.matcher(expr);
        if (!m.matches()) {
            return MatchResult.miss("crowdNum 阈值表达式非法：" + expr);
        }
        String op = m.group(1);
        int threshold = Integer.parseInt(m.group(2));
        int actual = in.crowdNum();
        boolean ok = switch (op) {
            case ">" -> actual > threshold;
            case ">=" -> actual >= threshold;
            case "<" -> actual < threshold;
            case "<=" -> actual <= threshold;
            case "=", "==" -> actual == threshold;
            default -> false;
        };
        return ok
                ? MatchResult.hit("crowdNum " + actual + " 命中阈值 " + expr.trim())
                : MatchResult.miss("crowdNum " + actual + " 未满足阈值 " + expr.trim());
    }

    private MatchResult matchDeviceTime(JSONObject cfg, RuleMatchInput in) {
        if (in.deviceNum() == null) {
            return MatchResult.miss("事件无设备编号");
        }
        // time_scope 已在门控阶段校验；此处仅判定设备名单
        List<String> devs = stringList(cfg.get("deviceNum"));
        return devs.contains(in.deviceNum())
                ? MatchResult.hit("设备 " + in.deviceNum() + " 在布控时段内")
                : MatchResult.miss("设备 " + in.deviceNum() + " 未命中布控名单");
    }

    /** 日内时段窗口判定，支持跨零点(如 22:00~06:00)。start/end 缺失或非法则放行。 */
    private boolean inTimeScope(JSONObject timeScope, LocalTime t) {
        LocalTime start = parseHHmm(timeScope.getStr("start"));
        LocalTime end = parseHHmm(timeScope.getStr("end"));
        if (start == null || end == null) {
            return true;
        }
        if (start.isBefore(end) || start.equals(end)) {
            return !t.isBefore(start) && !t.isAfter(end);   // [start,end] 同日
        }
        return !t.isBefore(start) || !t.isAfter(end);         // 跨零点：>=start 或 <=end
    }

    private LocalTime parseHHmm(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(s.trim(), HHMM);
        } catch (Exception e) {
            try {
                return LocalTime.parse(s.trim());  // 兼容 HH:mm:ss
            } catch (Exception ex) {
                log.warn("[RuleMatcher] time_scope 时间解析失败: {}", s);
                return null;
            }
        }
    }

    private List<String> parseStringArray(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return stringList(JSONUtil.parseArray(json));
        } catch (Exception e) {
            log.warn("[RuleMatcher] device_scope 解析失败: {}", json);
            return null;
        }
    }

    private JSONObject parseObj(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSONUtil.parseObj(json);
        } catch (Exception e) {
            log.warn("[RuleMatcher] JSON 解析失败: {}", json);
            return null;
        }
    }

    /** 将 JSONArray / List / 单值 统一转为 String 列表；null 安全(缺失键返回空表)。 */
    private List<String> stringList(Object node) {
        List<String> out = new ArrayList<>();
        if (node == null) {
            return out;
        }
        if (node instanceof Iterable<?> it) {
            for (Object o : it) {
                if (o != null) {
                    out.add(String.valueOf(o));
                }
            }
        } else {
            out.add(String.valueOf(node));
        }
        return out;
    }
}
