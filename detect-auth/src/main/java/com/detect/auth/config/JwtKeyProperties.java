package com.detect.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 签名与签发配置，前缀 detect.auth.jwt。
 */
@Data
@ConfigurationProperties(prefix = "detect.auth.jwt")
public class JwtKeyProperties {

    /** keystore 位置(classpath: 或 file:) */
    private String keyStore = "classpath:jwt.jks";

    /** keystore 密码 */
    private String keyStorePassword;

    /** 密钥别名 */
    private String keyAlias = "detect-jwt";

    /** 密钥密码(为空则同 keystore 密码) */
    private String keyPassword;

    /** 签发者 iss */
    private String issuer = "http://localhost:8081";

    /** access_token 有效期(秒) */
    private long expireSeconds = 7200;
}
