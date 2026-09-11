package com.detect.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RemoveFromHeaderGlobalFilter 单测：验证 from 头被剥离、其他头保留、无 from 头时透传。
 */
class RemoveFromHeaderGlobalFilterTest {

    private final RemoveFromHeaderGlobalFilter filter = new RemoveFromHeaderGlobalFilter();

    private ServerHttpRequest runThroughFilter(MockServerHttpRequest request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerHttpRequest> downstream = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            downstream.set(ex.getRequest());
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        return downstream.get();
    }

    @Test
    void filter_shouldStripFromHeaderKeepOthers() {
        ServerHttpRequest result = runThroughFilter(MockServerHttpRequest
                .get("/admin/event/event-records/page")
                .header("from", "Y")
                .header("Authorization", "Bearer x")
                .build());

        assertFalse(result.getHeaders().containsKey("from"), "from 头必须被剥离");
        assertTrue(result.getHeaders().containsKey("Authorization"), "其他头应保留");
    }

    @Test
    void filter_shouldPassThroughWhenNoFromHeader() {
        ServerHttpRequest result = runThroughFilter(MockServerHttpRequest
                .get("/auth/oauth/token")
                .header("Content-Type", "application/json")
                .build());

        assertFalse(result.getHeaders().containsKey("from"));
        assertTrue(result.getHeaders().containsKey("Content-Type"));
    }
}
