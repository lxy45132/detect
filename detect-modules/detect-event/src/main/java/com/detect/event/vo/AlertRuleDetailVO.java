package com.detect.event.vo;

import lombok.Data;

/**
 * 规则详情(接口文档 §4.2.2)：返回单条规则全字段(含 deviceScope/timeScope/remark)。
 *
 * <p>JSON 列(matchConfig/deviceScope/timeScope)解析为对象/数组直出；时间格式化为 {@code yyyy-MM-dd HH:mm:ss}(§2.4)。
 */
@Data
public class AlertRuleDetailVO {

    private Long id;
    private String ruleName;
    private String ruleType;
    /** 规则类型中文名(枚举翻译) */
    private String ruleTypeName;
    private Integer eventType;
    private Object matchConfig;
    private Integer priority;
    private Integer notifyEnabled;
    private Object deviceScope;
    private Object timeScope;
    private Integer enabled;
    private String remark;
    private String createBy;
    private String createTime;
    private String updateTime;
}
