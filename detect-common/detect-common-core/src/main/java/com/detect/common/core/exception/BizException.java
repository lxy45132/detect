package com.detect.common.core.exception;

import com.detect.common.core.enums.ResultCode;
import lombok.Getter;

/**
 * 业务异常：携带错误码，由 {@link GlobalExceptionHandler} 统一转成 R。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ResultCode rc) {
        super(rc.getMsg());
        this.code = rc.getCode();
    }

    public BizException(ResultCode rc, String msg) {
        super(msg);
        this.code = rc.getCode();
    }

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
    }
}
