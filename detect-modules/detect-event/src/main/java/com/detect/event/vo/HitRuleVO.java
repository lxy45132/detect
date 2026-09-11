package com.detect.event.vo;

/**
 * 命中规则摘要(接口文档 §4.1.3 detail.hitRule)：{@code {id, ruleName, ruleType}}。
 */
public record HitRuleVO(Long id, String ruleName, String ruleType) {
}
