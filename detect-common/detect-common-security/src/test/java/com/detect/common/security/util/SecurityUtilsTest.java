package com.detect.common.security.util;

import com.detect.common.security.model.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityUtilsTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldParseLoginUserFromJwt() {
        Map<String, Object> headers = Map.of("alg", "RS256");
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "admin");
        claims.put("username", "admin");
        claims.put("user_id", 1001);
        claims.put("authorities", List.of("ROLE_ADMIN", "event:read"));
        Jwt jwt = new Jwt("token-value", Instant.now(), Instant.now().plusSeconds(120), headers, claims);
        JwtAuthenticationToken auth = new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")), "admin");
        SecurityContextHolder.getContext().setAuthentication(auth);

        LoginUser user = SecurityUtils.getUser();

        assertNotNull(user);
        assertEquals(1001L, user.getUserId().longValue());
        assertEquals("admin", user.getUsername());
        assertTrue(user.getAuthorities().contains("event:read"));
        assertEquals(1001L, SecurityUtils.getUserId().longValue());
    }

    @Test
    void shouldReturnNullWhenNotAuthenticated() {
        assertNull(SecurityUtils.getUser());
    }
}
