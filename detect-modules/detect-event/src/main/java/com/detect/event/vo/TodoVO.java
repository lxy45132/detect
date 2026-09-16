package com.detect.event.vo;

import lombok.Data;

/**
 * 待办列表项(接口文档 §4.3.1)：结构同 §4.1.2 records({@link EventRecordListVO})，额外含 {@code hitRuleName}。
 *
 * <p>{@code eventTypeName} 由枚举翻译；{@code task} 从 source_data JSON 提取；{@code snapTime} 已格式化；
 * {@code hitRuleName} 由 {@code hitRuleId} 批量解析(未命中为 null)。
 */
@Data
public class TodoVO {

    private Long id;
    private String deviceNum;
    private String deviceName;
    private Integer eventType;
    /** 事件大类中文名(枚举翻译) */
    private String eventTypeName;
    /** 事件子类(source_data.task) */
    private String task;
    /** 抓拍时间，格式化 yyyy-MM-dd HH:mm:ss */
    private String snapTime;
    private String snapUrl;
    private String plateNum;
    private String vehicleNormalType;
    private Integer crowdNum;
    private Integer handleStatus;
    private Integer priority;
    private Long hitRuleId;
    /** 命中规则名(hitRuleId 解析，未命中为 null) */
    private String hitRuleName;
    /** AI 复核是否修正了原值(sourceData.aiReview.overridden) */
    private Boolean aiCorrected;
}
