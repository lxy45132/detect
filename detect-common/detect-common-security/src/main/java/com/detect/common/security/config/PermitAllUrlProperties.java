package com.detect.common.security.config;

import com.detect.common.core.annotation.Inner;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 扫描所有标注 {@link Inner} 的接口(方法或类)，收集其 URL 加入 permitAll 白名单。
 * 内部接口免 JWT，改由 {@code InnerAspect} 校验 from:Y 请求头。
 */
@Slf4j
public class PermitAllUrlProperties implements InitializingBean {

    private final RequestMappingHandlerMapping handlerMapping;

    @Getter
    private final List<String> urls = new ArrayList<>();

    public PermitAllUrlProperties(RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    @Override
    public void afterPropertiesSet() {
        Map<RequestMappingInfo, HandlerMethod> methods = handlerMapping.getHandlerMethods();
        Set<String> collected = new TreeSet<>();
        methods.forEach((info, handler) -> {
            boolean inner = handler.hasMethodAnnotation(Inner.class)
                    || handler.getBeanType().isAnnotationPresent(Inner.class);
            if (inner && info.getPathPatternsCondition() != null) {
                for (PathPattern pattern : info.getPathPatternsCondition().getPatterns()) {
                    collected.add(pattern.getPatternString());
                }
            }
        });
        urls.addAll(collected);
        log.info("[security] @Inner 免鉴权放行 URL: {}", urls);
    }
}
