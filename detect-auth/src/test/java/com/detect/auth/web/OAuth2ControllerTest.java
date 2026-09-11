package com.detect.auth.web;

import com.detect.auth.service.DetectUserDetails;
import com.detect.auth.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * OAuth2Controller 单测：验证密码换 token 成功响应体与认证失败的 OAuth2 错误结构。
 */
class OAuth2ControllerTest {

    @Test
    void token_success_shouldReturnBearerBody() {
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        JwtService jwtService = mock(JwtService.class);
        OAuth2Controller controller = new OAuth2Controller(authenticationManager, jwtService);

        DetectUserDetails principal = new DetectUserDetails(1L, "admin", "encoded", true,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtService.generateToken(eq(1L), eq("admin"), any())).thenReturn("mock.jwt.token");
        when(jwtService.getExpiresIn()).thenReturn(7200L);

        ResponseEntity<Map<String, Object>> response = controller.token("admin", "123456", "password", null);

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("mock.jwt.token", body.get("access_token"));
        assertEquals("Bearer", body.get("token_type"));
        assertEquals(7200L, body.get("expires_in"));
        assertEquals(1L, body.get("user_id"));
        assertEquals("admin", body.get("username"));
        assertEquals(List.of("ROLE_ADMIN"), body.get("authorities"));
    }

    @Test
    void token_badCredentials_shouldReturnInvalidGrant() {
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        JwtService jwtService = mock(JwtService.class);
        OAuth2Controller controller = new OAuth2Controller(authenticationManager, jwtService);
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        ResponseEntity<Map<String, Object>> response = controller.token("admin", "wrong", "password", null);

        assertEquals(400, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("invalid_grant", body.get("error"));
        assertNotNull(body.get("error_description"));
    }
}
