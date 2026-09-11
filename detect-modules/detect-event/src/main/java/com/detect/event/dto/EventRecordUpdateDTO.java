package com.detect.event.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 事件字段修正请求体(接口文档 §4.1.4)。仅传需修改的<b>业务字段</b>，服务端按非空增量更新。
 *
 * <p><b>不含 handleStatus</b>：处理状态流转走 {@code /alert-handles/process}(§4.3)，此处禁止越权改状态；
 * 也不含 deviceNum/eventType/snapTime/sourceData/status/hitRuleId 等系统字段。
 */
@Data
public class EventRecordUpdateDTO {

    private String name;
    private String cardno;
    private String libName;
    private Integer similarity;
    private String identifyFaceUrl;
    private String visibleLightUrl;

    private String plateNum;
    private Integer vehicleType;
    private String vehicleNormalType;
    private String vehicleLogo;
    private String vehicleSubLogo;
    private String vehicleColor;
    private String vehicleModel;
    private BigDecimal heightPermitted;
    private Integer crowdNum;
}
