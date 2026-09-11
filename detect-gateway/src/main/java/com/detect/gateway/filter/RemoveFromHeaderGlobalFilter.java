package com.detect.gateway.filter;

import com.detect.common.core.constant.CommonConstants;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 安全全局过滤器：剥离外部请求携带的 from 头，防止伪造 @Inner 内部调用。
 * <p>
 * Python 推送直连 detect-event(不经网关)，其 from:Y 不受影响；任何经网关的请求都会被移除
 * from 头，故外部无法冒充内部调用绕过鉴权命中 /event-records/receive 等 @Inner 接口。
 */
@Component
public class RemoveFromHeaderGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (!request.getHeaders().containsKey(CommonConstants.FROM)) {
            return chain.filter(exchange);
        }
        ServerHttpRequest mutated = request.mutate()
                .headers(headers -> headers.remove(CommonConstants.FROM))
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
