package com.detect.event.service;

import com.detect.event.entity.EventRecords;

import java.time.LocalDateTime;

/**
 * 规则匹配输入：归一化「持久化事件」与「试跑样本」两种来源，供 {@link RuleMatcher} 统一评估。
 * 仅承载规则判定所需字段(§3.1 ruleType 匹配依据)，与 DTO/实体解耦。
 */
public record RuleMatchInput(
        Integer eventType,
        String deviceNum,
        String plateNum,
        Integer vehicleType,
        String vehicleNormalType,
        Integer crowdNum,
        LocalDateTime snapTime
) {

    /** 从已入库事件适配(6c-2 队列消费者用)。 */
    public static RuleMatchInput from(EventRecords e) {
        return new RuleMatchInput(e.getEventType(), e.getDeviceNum(), e.getPlateNum(),
                e.getVehicleType(), e.getVehicleNormalType(), e.getCrowdNum(), e.getSnapTime());
    }
}
