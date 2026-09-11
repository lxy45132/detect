package com.detect.event;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 事件管理后台启动类。
 *
 * <p>承载 5 个业务域：事件记录 / 布控规则 / 预警处理 / 站内通知 / 分类字典。
 * 作为 OAuth2 资源服务器校验网关透传的 JWT；{@code @Inner} 接口(Python webhook)免鉴权。
 *
 * <ul>
 *   <li>{@link MapperScan} 扫描 MyBatis Mapper</li>
 *   <li>{@link EnableFeignClients} 启用 Feign(扇出通知时调用 auth 列管理员)</li>
 * </ul>
 */
@SpringBootApplication
@MapperScan("com.detect.event.mapper")
@EnableFeignClients
public class EventApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventApplication.class, args);
    }
}
