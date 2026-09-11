package com.detect.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 网关启动类(响应式 Spring Cloud Gateway)。
 * 职责：Nacos 动态路由(lb://) + 全局 CORS + 剥离外部伪造的 from 头；
 * JWT 验签下沉到各资源服务(detect-event 复用 common-security)，网关自身不做鉴权。
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
