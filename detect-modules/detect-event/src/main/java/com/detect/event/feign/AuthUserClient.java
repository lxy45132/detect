package com.detect.event.feign;

import com.detect.common.core.domain.R;
import com.detect.event.dto.AdminUser;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * 调用 detect-auth 内部接口列管理员(扇出预警通知用)。经 Nacos 服务发现 + LoadBalancer 直连(不走网关)。
 * {@link InnerFeignConfig} 补 {@code from: Y} 头以过 auth 的 {@code @Inner} 校验。
 */
@FeignClient(name = "detect-auth", contextId = "authUserClient",
        path = "/inner/users", configuration = InnerFeignConfig.class)
public interface AuthUserClient {

    /** 列出所有启用的管理员。 */
    @GetMapping("/admins")
    R<List<AdminUser>> listAdmins();
}
