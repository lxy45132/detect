package com.detect.common.core.exception;

import com.detect.common.core.domain.R;
import com.detect.common.core.enums.ResultCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：把各类异常统一转成 {@link R}。
 * 由 {@code CoreAutoConfiguration} 在 Servlet Web 应用中自动注册。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException e) {
        log.warn("业务异常 code={} msg={}", e.getCode(), e.getMessage());
        return R.failed(e.getCode(), e.getMessage());
    }

    /** @RequestBody @Valid 失败(MethodArgumentNotValidException 是 BindException 子类) */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBind(BindException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = (fe == null) ? ResultCode.BAD_REQUEST.getMsg()
                : fe.getField() + " " + fe.getDefaultMessage();
        return R.failed(ResultCode.BAD_REQUEST.getCode(), msg);
    }

    /** 方法参数级 @Validated 失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> handleConstraint(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse(ResultCode.BAD_REQUEST.getMsg());
        return R.failed(ResultCode.BAD_REQUEST.getCode(), msg);
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return R.failed(ResultCode.SYSTEM_ERROR.getCode(), ResultCode.SYSTEM_ERROR.getMsg());
    }
}
