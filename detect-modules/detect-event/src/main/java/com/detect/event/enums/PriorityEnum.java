package com.detect.event.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 优先级，对齐接口文档 §3.1。数值越大越紧急(排序 priority DESC 置顶)。
 */
@Getter
@AllArgsConstructor
public enum PriorityEnum {

    NORMAL(0, "普通"),
    IMPORTANT(1, "重要"),
    URGENT(2, "紧急");

    private final int code;
    private final String name;

    /** 按 code 取中文名，未匹配返回 null。 */
    public static String nameOf(Integer code) {
        if (code == null) {
            return null;
        }
        for (PriorityEnum p : values()) {
            if (p.code == code) {
                return p.name;
            }
        }
        return null;
    }
}
