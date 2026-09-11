package com.detect.event.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 处理状态，对齐接口文档 §3.1 与 §5 状态机。
 * {@link #RESOLVED}、{@link #IGNORED} 为终态，不可再流转。
 */
@Getter
@AllArgsConstructor
public enum HandleStatusEnum {

    PENDING(0, "未处理"),
    PROCESSING(1, "处理中"),
    RESOLVED(2, "已处理"),
    IGNORED(3, "误报忽略");

    private final int code;
    private final String name;

    /** 按 code 取中文名，未匹配返回 null。 */
    public static String nameOf(Integer code) {
        if (code == null) {
            return null;
        }
        for (HandleStatusEnum h : values()) {
            if (h.code == code) {
                return h.name;
            }
        }
        return null;
    }
}
