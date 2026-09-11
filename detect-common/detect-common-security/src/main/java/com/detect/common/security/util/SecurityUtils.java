package com.detect.common.security.util;

import com.detect.common.security.model.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 安全上下文工具：从 JWT 认证信息中解析当前登录用户。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * 解析当前登录用户；未认证或非 JWT 认证时返回 null。
     */
    public static LoginUser getUser() {
        Authentication auth = getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken)) {
            return null;
        }
        Jwt jwt = ((JwtAuthenticationToken) auth).getToken();

        Long userId = null;
        Object uid = jwt.getClaim("user_id");
        if (uid instanceof Number) {
            userId = ((Number) uid).longValue();
        } else if (uid != null) {
            try {
                userId = Long.parseLong(uid.toString());
            } catch (NumberFormatException ignored) {
                // 声明格式非法时忽略，userId 保持 null
            }
        }

        String username = jwt.getClaimAsString("username");
        if (username == null) {
            username = jwt.getSubject();
        }

        List<String> authorities = jwt.getClaimAsStringList("authorities");
        if (authorities == null || authorities.isEmpty()) {
            authorities = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());
        }
        return new LoginUser(userId, username, authorities);
    }

    public static Long getUserId() {
        LoginUser u = getUser();
        return u == null ? null : u.getUserId();
    }

    public static String getUsername() {
        LoginUser u = getUser();
        return u == null ? null : u.getUsername();
    }
}
