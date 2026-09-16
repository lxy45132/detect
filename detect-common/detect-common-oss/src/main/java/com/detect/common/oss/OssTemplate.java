package com.detect.common.oss;

import com.detect.common.core.enums.ResultCode;
import com.detect.common.core.exception.BizException;
import com.detect.common.oss.config.OssProperties;
import com.detect.common.oss.util.OssUtils;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MinIO 操作模板：上传(字节/base64)、下载、删除、预签名 URL、桶自举。
 * 所有 MinIO 受检异常统一包装为 {@link BizException}。
 */
@Slf4j
public class OssTemplate {

    private final MinioClient client;
    private final OssProperties props;
    private final AtomicBoolean bucketReady = new AtomicBoolean(false);

    public OssTemplate(MinioClient client, OssProperties props) {
        this.client = client;
        this.props = props;
    }

    /** 上传字节流，返回稳定可访问 URL */
    public String upload(byte[] bytes, String objectName, String contentType) {
        ensureBucket();
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(props.getBucket())
                    .object(objectName)
                    .stream(is, bytes.length, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
        } catch (Exception e) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "文件上传失败: " + e.getMessage());
        }
        return getUrl(objectName);
    }

    /** 上传 base64 图片(自动剥离 data URI 前缀)，对象键按 dir/日期/uuid 生成，返回可访问 URL */
    public String uploadBase64(String base64, String dir, String originalFilename, String contentType) {
        byte[] bytes = OssUtils.decodeBase64(base64);
        String objectName = OssUtils.generateObjectKey(dir, originalFilename);
        return upload(bytes, objectName, contentType);
    }

    public InputStream download(String objectName) {
        try {
            return client.getObject(GetObjectArgs.builder()
                    .bucket(props.getBucket())
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "文件下载失败: " + e.getMessage());
        }
    }

    public void remove(String objectName) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(props.getBucket())
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "文件删除失败: " + e.getMessage());
        }
    }

    /** 生成临时预签名访问 URL */
    public String getPresignedUrl(String objectName, int expireSeconds) {
        try {
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(props.getBucket())
                    .object(objectName)
                    .expiry(expireSeconds)
                    .build());
        } catch (Exception e) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "获取预签名URL失败: " + e.getMessage());
        }
    }

    /** 拼接稳定可访问 URL(桶需公读或经网关代理) */
    public String getUrl(String objectName) {
        String base = (props.getPublicUrl() == null || props.getPublicUrl().isBlank())
                ? props.getEndpoint() : props.getPublicUrl();
        return OssUtils.buildUrl(base, props.getBucket(), objectName);
    }

    /**
     * 把库里存的稳定 URL 换成限时预签名 URL，使用配置的默认有效期。
     *
     * <p>用于「桶保持私有 + 浏览器 {@code <img>} 直接取图」的场景：签名自带凭据
     * (X-Amz-Signature 等 query 参数)，MinIO 直接校验，不需要匿名读策略，也不需要
     * 前端带 Authorization 头({@code <img>} 根本不会带)。
     *
     * <p><b>降级保证</b>：入参为空、或反解不出对象键(不是本桶的 URL，例如人脸库供应商外链)
     * 时<b>原样返回</b>，不报错也不返回 null —— 读出侧不能因为一张图而让整个列表/详情接口 500。
     */
    public String toPresignedUrl(String storedUrl) {
        return toPresignedUrl(storedUrl, props.getPresignExpireSeconds());
    }

    /** 同 {@link #toPresignedUrl(String)}，但指定有效期(秒) */
    public String toPresignedUrl(String storedUrl, int expireSeconds) {
        if (storedUrl == null || storedUrl.isBlank()) {
            return storedUrl;
        }
        String base = (props.getPublicUrl() == null || props.getPublicUrl().isBlank())
                ? props.getEndpoint() : props.getPublicUrl();
        String objectName = OssUtils.extractObjectName(storedUrl, base, props.getBucket());
        if (objectName == null) {
            log.warn("[oss] 无法从 URL 反解对象键，原样返回(不属本桶或配置不匹配): {}", storedUrl);
            return storedUrl;
        }
        return getPresignedUrl(objectName, expireSeconds);
    }

    /** 首次写入时确保桶存在(不存在则创建)，仅检查一次 */
    private void ensureBucket() {
        if (bucketReady.get()) {
            return;
        }
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(props.getBucket()).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(props.getBucket()).build());
                log.info("[oss] 创建存储桶: {}", props.getBucket());
            }
            bucketReady.set(true);
        } catch (Exception e) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "初始化存储桶失败: " + e.getMessage());
        }
    }
}
