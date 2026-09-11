package com.detect.event.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 事件大类，对齐 Python {@code SourceCategoryEnum} 与接口文档 §3.1。
 */
@Getter
@AllArgsConstructor
public enum EventTypeEnum {

    FACE(100, "人脸"),
    VEHICLE(200, "车辆"),
    CROWD(300, "聚集");

    private final int code;
    private final String name;

    /** 按 code 取中文名，未匹配返回 null。 */
    public static String nameOf(Integer code) {
        if (code == null) {
            return null;
        }
        for (EventTypeEnum e : values()) {
            if (e.code == code) {
                return e.name;
            }
        }
        return null;
    }
}
