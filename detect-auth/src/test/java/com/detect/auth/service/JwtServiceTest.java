package com.detect.auth.service;

import com.detect.auth.config.JwtKeyProperties;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JwtService 单测：用内存 RSAKey(不依赖 jwt.jks)验证签发声明契约与 JWK 公钥暴露。
 */
class JwtServiceTest {

    private static RSAKey rsaKey;
    private JwtService jwtService;

    @BeforeAll
    static void generateKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID("test-key")
                .build();
    }

    @BeforeEach
    void setUp() {
        JwtKeyProperties props = new JwtKeyProperties();
        props.setIssuer("http://localhost:8081");
        props.setExpireSeconds(7200);
        jwtService = new JwtService(rsaKey, props);
    }

    @Test
    @SuppressWarnings("unchecked")
    void generateToken_shouldCarryContractClaims() throws Exception {
        String token = jwtService.generateToken(1L, "admin", List.of("ROLE_ADMIN"));

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
        Jwt jwt = decoder.decode(token);

        assertEquals("admin", jwt.getSubject());
        assertEquals("http://localhost:8081", jwt.getIssuer().toString());
        assertEquals(1L, ((Number) jwt.getClaim("user_id")).longValue());
        assertEquals("admin", jwt.getClaim("username"));
        List<String> authorities = (List<String>) jwt.getClaim("authorities");
        assertTrue(authorities.contains("ROLE_ADMIN"));
        assertNotNull(jwt.getIssuedAt());
        assertNotNull(jwt.getExpiresAt());
    }

    @Test
    @SuppressWarnings("unchecked")
    void jwkSet_shouldExposePublicKeyOnly() {
        Map<String, Object> jwks = jwtService.jwkSet();
        List<Map<String, Object>> keys = (List<Map<String, Object>>) jwks.get("keys");

        assertEquals(1, keys.size());
        Map<String, Object> key = keys.get(0);
        assertEquals("test-key", key.get("kid"));
        assertNotNull(key.get("n"), "公钥模数 n 必须存在");
        assertNull(key.get("d"), "私钥指数 d 绝不能暴露");
    }
}
