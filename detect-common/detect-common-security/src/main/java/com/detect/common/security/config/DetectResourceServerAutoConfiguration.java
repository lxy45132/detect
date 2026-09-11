package com.detect.common.security.config;

import com.detect.common.security.handler.DetectAccessDeniedHandler;
import com.detect.common.security.handler.DetectAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 资源服务器自动配置(仅 Servlet Web 应用生效)：
 * 无状态 JWT 校验、@Inner 放行、401/403 统一 R JSON。
 * 各服务通过 spring.security.oauth2.resourceserver.jwt.jwk-set-uri 指定认证服务器公钥。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(SecurityFilterChain.class)
@EnableWebSecurity
public class DetectResourceServerAutoConfiguration {

    /**
     * 注入主 MVC 的 {@code requestMappingHandlerMapping}(@Inner 控制器注册于此)。
     * 显式 {@link Qualifier} 是必需的：引入 actuator 后会额外注册 {@code controllerEndpointHandlerMapping}，
     * 同类型 2 个 bean 会使按类型注入产生歧义导致启动失败。
     */
    @Bean
    public PermitAllUrlProperties permitAllUrlProperties(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        return new PermitAllUrlProperties(handlerMapping);
    }

    @Bean
    public SecurityFilterChain detectSecurityFilterChain(HttpSecurity http,
                                                         PermitAllUrlProperties permitAll) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(reg -> {
                    for (String url : permitAll.getUrls()) {
                        reg.requestMatchers(url).permitAll();
                    }
                    reg.requestMatchers("/actuator/**", "/error").permitAll();
                    reg.anyRequest().authenticated();
                })
                .oauth2ResourceServer(rs -> rs.jwt(
                        jwt -> jwt.jwtAuthenticationConverter(new DetectJwtAuthenticationConverter())))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new DetectAuthenticationEntryPoint())
                        .accessDeniedHandler(new DetectAccessDeniedHandler()));
        return http.build();
    }
}
