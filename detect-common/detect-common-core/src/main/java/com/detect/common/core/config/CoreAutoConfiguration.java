package com.detect.common.core.config;

import com.detect.common.core.aspect.InnerAspect;
import com.detect.common.core.exception.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

/**
 * common-core 自动配置：仅在 Servlet Web 应用中注册全局异常处理与 @Inner 切面。
 * reactive 网关(ConditionalOnWebApplication.Type.SERVLET 不满足)不会加载，避免 servlet 依赖缺失。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import({GlobalExceptionHandler.class, InnerAspect.class})
public class CoreAutoConfiguration {
}
