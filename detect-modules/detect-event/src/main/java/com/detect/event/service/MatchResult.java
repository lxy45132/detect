package com.detect.event.service;

/**
 * 规则匹配结果：命中与否 + 人类可读原因。
 *
 * <p>形状即 §4.2.7 规则试跑响应 {@code {matched, reason}}，故 match-test 端点直接返回本类型，
 * 无需额外 VO(避免重复)。reason 命中示例：{@code "crowdNum 12 命中阈值 >10"}。
 */
public record MatchResult(boolean matched, String reason) {

    public static MatchResult hit(String reason) {
        return new MatchResult(true, reason);
    }

    public static MatchResult miss(String reason) {
        return new MatchResult(false, reason);
    }
}
