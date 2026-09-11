package com.detect.event.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 事件子类(存于 {@code source_data.task})，对齐接口文档 §3.1。
 * {@code ship_plate} 为预留：当前不在 Python 推送任务列表。
 */
@Getter
@AllArgsConstructor
public enum TaskTypeEnum {

    FACE_CAPTURE("face_capture", "人脸抓拍", 100),
    VEHICLE_TYPE("vehicle_type", "车辆类型", 200),
    LICENSE_PLATE("license_plate", "车牌识别", 200),
    PLATE_UNRECOGNIZED("plate_unrecognized", "车牌未识别", 200),
    PEOPLE_GATHERING("people_gathering", "人员聚集", 300),
    SHIP_PLATE("ship_plate", "船舶舷号", 200);

    private final String code;
    private final String name;
    private final int eventType;

    /** 按 code 取中文名，未匹配返回 null。 */
    public static String nameOf(String code) {
        if (code == null) {
            return null;
        }
        for (TaskTypeEnum t : values()) {
            if (t.code.equals(code)) {
                return t.name;
            }
        }
        return null;
    }
}
