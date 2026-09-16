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

    /**
     * 预签名 URL 有效期(秒)，默认 30 分钟。
     *
     * <p>桶保持私有时，读出侧把库里存的稳定 URL 换成限时签名 URL 交给前端，
     * 浏览器 {@code <img>} 无需携带任何凭据即可取图。太短会让用户停留页面后
     * 点放大失效，太长等于变相公开，30 分钟是两者的折中。
     */
    private int presignExpireSeconds = 1800;
}
