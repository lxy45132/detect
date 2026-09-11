package com.detect.auth.service;

import com.detect.auth.config.JwtKeyProperties;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * JWT 签发服务：用 RSAKey 签名，产出 access_token；并对外暴露公钥 JWK Set。
 * 声明契约(与 common-security 对齐)：iss / sub=username / user_id / username / authorities / iat / exp。
 */
@Slf4j
@Service
public class JwtService {

    private final JwtKeyProperties props;
    private final RSAKey rsaKey;
    private final JwtEncoder jwtEncoder;

    public JwtService(RSAKey jwtRsaKey, JwtKeyProperties props) {
        this.rsaKey = jwtRsaKey;
        this.props = props;
        JWKSource<SecurityContext> jwkSource = (selector, ctx) -> selector.select(new JWKSet(rsaKey));
        this.jwtEncoder = new NimbusJwtEncoder(jwkSource);
    }

    /** 签发 access_token(JWT 字符串) */
    public String generateToken(Long userId, String username, List<String> authorities) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.getIssuer())
                .subject(username)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(props.getExpireSeconds()))
                .claim("user_id", userId)
                .claim("username", username)
                .claim("authorities", authorities)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    /** access_token 有效期(秒) */
    public long getExpiresIn() {
        return props.getExpireSeconds();
    }

    /** JWK Set JSON(仅公钥)，供资源服务器 /oauth2/jwks 验签 */
    public Map<String, Object> jwkSet() {
        return new JWKSet(rsaKey.toPublicJWK()).toJSONObject();
    }
}
