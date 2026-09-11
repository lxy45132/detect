package com.detect.event.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 布控规则类型，对齐接口文档 §3.1。决定 {@code match_config} 的解析方式与匹配依据字段。
 */
@Getter
@AllArgsConstructor
public enum RuleTypeEnum {

    /** 车牌黑名单：匹配 {@code plate_num} ∈ matchConfig.plateNum */
    PLATE_BLACKLIST("PLATE_BLACKLIST", "车牌黑名单"),
    /** 车辆类型：匹配 {@code vehicle_normal_type} ∈ matchConfig.vehicleNormalType */
    VEHICLE_TYPE("VEHICLE_TYPE", "车辆类型"),
    /** 聚集人数阈值：匹配 {@code crowd_num} 满足 matchConfig.crowdNum(如 ">10") */
    CROWD_THRESHOLD("CROWD_THRESHOLD", "聚集人数阈值"),
    /** 设备时段：匹配 {@code device_num} ∈ matchConfig.deviceNum 且落在 time_scope */
    DEVICE_TIME("DEVICE_TIME", "设备时段");

    private final String code;
    private final String name;

    /** 校验 code 是否为合法规则类型。 */
    public static boolean isValid(String code) {
        if (code == null) {
            return false;
        }
        for (RuleTypeEnum r : values()) {
            if (r.code.equals(code)) {
                return true;
            }
        }
        return false;
    }

    /** 按 code 取中文名，未匹配返回 null。 */
    public static String nameOf(String code) {
        if (code == null) {
            return null;
        }
        for (RuleTypeEnum r : values()) {
            if (r.code.equals(code)) {
                return r.name;
            }
        }
        return null;
    }
}
