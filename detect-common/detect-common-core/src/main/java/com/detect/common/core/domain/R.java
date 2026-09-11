package com.detect.common.core.domain;

import com.detect.common.core.constant.CommonConstants;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应包装：{code, msg, data}。成功 code=0。
 *
 * @param <T> 业务数据类型
 */
@Data
public class R<T> implements Serializable {

    private int code;
    private String msg;
    private T data;

    public R() {
    }

    public R(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> R<T> ok() {
        return new R<>(CommonConstants.SUCCESS, "success", null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(CommonConstants.SUCCESS, "success", data);
    }

    public static <T> R<T> ok(T data, String msg) {
        return new R<>(CommonConstants.SUCCESS, msg, data);
    }

    public static <T> R<T> failed() {
        return new R<>(CommonConstants.FAIL, "失败", null);
    }

    public static <T> R<T> failed(String msg) {
        return new R<>(CommonConstants.FAIL, msg, null);
    }

    public static <T> R<T> failed(int code, String msg) {
        return new R<>(code, msg, null);
    }

    public boolean isSuccess() {
        return this.code == CommonConstants.SUCCESS;
    }
}
