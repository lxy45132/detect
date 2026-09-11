package com.detect.common.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记内部接口：仅允许携带 {@code from: Y} 请求头的调用访问(如 Python webhook 推送)。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Inner {

    /** 是否校验 from 头，默认校验 */
    boolean value() default true;
}
