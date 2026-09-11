package com.detect.common.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 全局错误码，对齐《事件管理后台 API 接口文档》附录 A。
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(0, "success"),
    BAD_REQUEST(400, "参数错误"),
    UNAUTHORIZED(401, "未认证"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    SYSTEM_ERROR(500, "系统异常"),

    EVENT_NOT_FOUND(1001, "事件不存在"),
    EVENT_DELETED(1002, "事件已删除"),

    RULE_CONFIG_INVALID(2001, "规则配置非法"),
    RULE_NOT_FOUND(2002, "规则不存在"),

    STATUS_TRANSITION_INVALID(3001, "状态流转非法"),

    EXPORT_LIMIT_EXCEEDED(4001, "导出数量超限");

    private final int code;
    private final String msg;
}
