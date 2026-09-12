package com.detect.event.feign;

import com.detect.common.core.constant.CommonConstants;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

/**
 * Feign 客户端专属配置：为出站请求补 {@code from: Y} 头，以通过目标服务 {@code @Inner} 校验。
 *
 * <p><b>刻意不加 {@code @Configuration}</b>——否则会被组件扫描收为全局配置、污染所有 Feign 客户端；
 * 仅经 {@code @FeignClient(configuration = InnerFeignConfig.class)} 作用于声明它的客户端。
 */
public class InnerFeignConfig {

    @Bean
    public RequestInterceptor innerFromHeaderInterceptor() {
        return template -> template.header(CommonConstants.FROM, CommonConstants.FROM_IN);
    }
}
