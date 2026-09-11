package com.detect.common.security.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetectJwtAuthenticationConverterTest {

    @Test
    void shouldMapAuthoritiesClaimAndUsername() {
        Map<String, Object> headers = Map.of("alg", "RS256");
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "admin");
        claims.put("username", "admin");
        claims.put("authorities", List.of("ROLE_ADMIN", "event:read"));
        Jwt jwt = new Jwt("token-value", Instant.now(), Instant.now().plusSeconds(120), headers, claims);

        var auth = new DetectJwtAuthenticationConverter().convert(jwt);

        assertNotNull(auth);
        assertEquals("admin", auth.getName());
        assertTrue(auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals));
    }
}
