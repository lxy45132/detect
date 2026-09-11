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
