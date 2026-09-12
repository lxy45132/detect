package com.detect.event.dto;

import lombok.Data;

/**
 * 规则试跑请求体(接口文档 §4.2.7)：传一条事件样本，返回是否命中及原因，<b>不落库</b>。
 */
@Data
public class MatchTestDTO {

    /** 待试跑的规则 ID */
    private Long ruleId;
    /** 事件样本(字段可缺省，缺失即不参与该项判定) */
    private SampleEvent sampleEvent;

    /**
     * 事件样本，字段对齐 {@link com.detect.event.service.RuleMatchInput}；
     * {@code snapTime} 为 {@code yyyy-MM-dd HH:mm:ss}(§4.2.7 示例)，解析失败则时段门控放行。
     */
    @Data
    public static class SampleEvent {
        private Integer eventType;
        private String deviceNum;
        private String plateNum;
        private Integer vehicleType;
        private String vehicleNormalType;
        private Integer crowdNum;
        private String snapTime;
    }
}
