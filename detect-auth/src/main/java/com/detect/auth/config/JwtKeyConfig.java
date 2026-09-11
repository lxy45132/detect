package com.detect.auth.config;

import com.nimbusds.jose.jwk.RSAKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * 从 JKS keystore 加载 RSA 密钥对，构造 nimbus RSAKey(含私钥，用于签发)。
 * keystore 缺失时启动即失败(fail-fast)。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(JwtKeyProperties.class)
public class JwtKeyConfig {

    private final ResourceLoader resourceLoader;

    @Bean
    public RSAKey jwtRsaKey(JwtKeyProperties props) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (InputStream is = resourceLoader.getResource(props.getKeyStore()).getInputStream()) {
            keyStore.load(is, props.getKeyStorePassword().toCharArray());
        }
        String keyPwd = (props.getKeyPassword() == null || props.getKeyPassword().isBlank())
                ? props.getKeyStorePassword() : props.getKeyPassword();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyStore.getKey(props.getKeyAlias(), keyPwd.toCharArray());
        Certificate cert = keyStore.getCertificate(props.getKeyAlias());
        RSAPublicKey publicKey = (RSAPublicKey) cert.getPublicKey();
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(props.getKeyAlias())
                .build();
        log.info("[auth] 加载 JWT keystore: {}, alias={}", props.getKeyStore(), props.getKeyAlias());
        return rsaKey;
    }
}
