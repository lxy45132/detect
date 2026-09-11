package com.detect.common.oss.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对象存储(MinIO)配置，前缀 detect.oss。
 */
@Data
@ConfigurationProperties(prefix = "detect.oss")
public class OssProperties {

    /** MinIO 服务端点，如 http://localhost:9000 */
    private String endpoint;

    /** 访问密钥(access key) */
    private String accessKey;

    /** 私密密钥(secret key) */
    private String secretKey;

    /** 默认桶名 */
    private String bucket = "detect";

    /** 对外访问基础 URL(拼接可访问地址)，为空则回退 endpoint */
    private String publicUrl;
}
