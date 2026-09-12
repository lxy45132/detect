package com.detect.event.vo;

import lombok.Data;

/**
 * 规则分页列表项(接口文档 §4.2.1 响应 records[])。
 *
 * <p>{@code ruleTypeName} 由 ruleType 经枚举翻译(附加，便于前端)；{@code matchConfig} 已从 JSON 串解析为对象直出。
 */
@Data
public class AlertRuleListVO {

    private Long id;
    private String ruleName;
    private String ruleType;
    /** 规则类型中文名(枚举翻译) */
    private String ruleTypeName;
    private Integer eventType;
    /** 匹配配置(解析后的 JSON 对象) */
    private Object matchConfig;
    private Integer priority;
    private Integer notifyEnabled;
    private Integer enabled;
}
