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

    /**
     * 状态流转合法性校验(接口文档 §5.2 矩阵)。
     *
     * <pre>
     * from\to   0    1    2    3
     *   0       —    ✓    ✗    ✓      未处理 → 处理中 / 误报
     *   1       ✗    —    ✓    ✓      处理中 → 已处理 / 误报
     *   2       ✗    ✗    —    ✗      已处理(终态)
     *   3       ✗    ✗    ✗    —      误报(终态)
     * </pre>
     *
     * <p>对角线(同状态)恒非法；{@link #RESOLVED}/{@link #IGNORED} 为终态无出边；
     * 目标非 0~3 或入参为 null 一律非法。非法流转由服务层抛 {@code 3001}。
     *
     * @param from 原状态 code
     * @param to   目标状态 code
     * @return 合法返回 true
     */
    public static boolean canTransition(Integer from, Integer to) {
        if (from == null || to == null) {
            return false;
        }
        return switch (from) {
            case 0 -> to == 1 || to == 3;
            case 1 -> to == 2 || to == 3;
            default -> false;
        };
    }
}
