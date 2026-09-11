package com.detect.auth.web;

import com.detect.auth.service.DetectUserDetails;
import com.detect.auth.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * OAuth2 端点：/oauth/token(密码换 JWT) 与 /oauth2/jwks(公钥)。均免鉴权。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class OAuth2Controller {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    /** 用户名/密码换取 access_token；失败返回 OAuth2 风格 error。 */
    @PostMapping("/oauth/token")
    public ResponseEntity<Map<String, Object>> token(
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            @RequestParam(value = "grant_type", required = false, defaultValue = "password") String grantType,
            @RequestParam(value = "scope", required = false) String scope) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
            DetectUserDetails principal = (DetectUserDetails) auth.getPrincipal();
            List<String> authorities = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());
            String accessToken = jwtService.generateToken(
                    principal.getUserId(), principal.getUsername(), authorities);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("access_token", accessToken);
            body.put("token_type", "Bearer");
            body.put("expires_in", jwtService.getExpiresIn());
            body.put("user_id", principal.getUserId());
            body.put("username", principal.getUsername());
            body.put("authorities", authorities);
            if (scope != null) {
                body.put("scope", scope);
            }
            return ResponseEntity.ok(body);
        } catch (AuthenticationException e) {
            log.warn("登录失败 username={}, reason={}", username, e.getMessage());
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "invalid_grant");
            err.put("error_description", "用户名或密码错误");
            return ResponseEntity.badRequest().body(err);
        }
    }

    /** JWK Set(公钥)，资源服务器据此验签 JWT。 */
    @GetMapping(value = "/oauth2/jwks", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> jwks() {
        return jwtService.jwkSet();
    }
}
