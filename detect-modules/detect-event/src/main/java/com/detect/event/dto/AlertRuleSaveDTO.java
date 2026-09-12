package com.detect.event.dto;

import lombok.Data;

/**
 * 规则新增/修改请求体(接口文档 §4.2.3 / §4.2.4)。
 *
 * <p>新增：ruleName/ruleType/matchConfig 必填，priority 默认 1、notifyEnabled 默认 1、enabled 默认 1。
 * 修改：<b>传需修改字段</b>，服务层按非空增量更新；ruleType/matchConfig 任一变更都会用「生效后组合」重新校验(2001)。
 *
 * <p>matchConfig/deviceScope/timeScope 以 JSON 承载：matchConfig 为对象、deviceScope 为数组、timeScope 为 {start,end} 对象。
 * 校验在服务层执行(不依赖 bean-validation 是否生效)。
 */
@Data
public class AlertRuleSaveDTO {

    /** 规则名称(新增必填) */
    private String ruleName;
    /** 规则类型(新增必填)，见 {@link com.detect.event.enums.RuleTypeEnum} */
    private String ruleType;
    /** 适用事件大类，null=全部 */
    private Integer eventType;
    /** 匹配配置(JSON 对象)，如 {"crowdNum":">10"} */
    private Object matchConfig;
    /** 命中优先级 0/1/2，默认 1 */
    private Integer priority;
    /** 是否生成站内通知 0/1，默认 1 */
    private Integer notifyEnabled;
    /** 生效设备(JSON 数组)，null=全部 */
    private Object deviceScope;
    /** 生效时段(JSON 对象 {start,end})，null=不限 */
    private Object timeScope;
    /** 启用状态 0/1，默认 1 */
    private Integer enabled;
    /** 备注 */
    private String remark;
}
