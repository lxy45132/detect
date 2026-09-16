package com.detect.event.vo;

import lombok.Data;

/**
 * 事件分页列表项(接口文档 §4.1.2 响应 records[])。
 *
 * <p>{@code eventTypeName} 由 {@code eventType} 经枚举翻译；{@code task} 从 {@code source_data} JSON 提取；
 * {@code snapTime} 已格式化为 {@code yyyy-MM-dd HH:mm:ss}(§2.4)，故用 String 直出。
 */
@Data
public class EventRecordListVO {

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
    /** AI 复核是否修正了原值(sourceData.aiReview.overridden) */
    private Boolean aiCorrected;
}
