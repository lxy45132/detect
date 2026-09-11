package com.detect.common.oss.config;

import com.detect.common.oss.OssTemplate;
import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * OSS 自动配置：仅当配置了 detect.oss.endpoint 时装配 MinioClient 与 OssTemplate。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "detect.oss", name = "endpoint")
@EnableConfigurationProperties(OssProperties.class)
public class OssAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MinioClient minioClient(OssProperties props) {
        return MinioClient.builder()
                .endpoint(props.getEndpoint())
                .credentials(props.getAccessKey(), props.getSecretKey())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public OssTemplate ossTemplate(MinioClient minioClient, OssProperties props) {
        return new OssTemplate(minioClient, props);
    }
}
