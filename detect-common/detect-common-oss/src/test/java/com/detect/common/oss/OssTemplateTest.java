package com.detect.common.oss;

import com.detect.common.oss.config.OssProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link OssTemplate#toPresignedUrl(String)} 的**降级分支**单测。
 *
 * <p>只测不触碰 MinIO client 的路径（入参为空 / 反解不出对象键 → 原样返回），
 * 故可以直接 {@code new OssTemplate(null, props)} 构造，无需 mock SDK；
 * 真正签名那一步是 MinIO SDK 的行为，由运行时联调验证（前端能看到图即证明签名有效）。
 *
 * <p>这两条分支是读出侧的安全网：列表/详情接口不能因为一张图反解失败就 500。
 */
class OssTemplateTest {

    private OssTemplate template(String publicUrl) {
        OssProperties props = new OssProperties();
        props.setEndpoint("http://localhost:9000");
        props.setAccessKey("minioadmin");
        props.setSecretKey("minioadmin");
        props.setBucket("detect");
        props.setPublicUrl(publicUrl);
        return new OssTemplate(null, props);
    }

    @Test
    void toPresignedUrlShouldReturnNullAndBlankAsIs() {
        OssTemplate oss = template("http://localhost:9000");
        assertNull(oss.toPresignedUrl(null));
        assertEquals("", oss.toPresignedUrl(""));
        assertEquals("   ", oss.toPresignedUrl("   "));
    }

    @Test
    void toPresignedUrlShouldReturnForeignUrlAsIsInsteadOfFailing() {
        OssTemplate oss = template("http://localhost:9000");
        // 不是本桶的 URL（例如人脸库供应商外链）：反解不出对象键 → 原样返回，不抛异常
        String foreign = "https://vendor.com/face/lib/a.jpg";
        assertEquals(foreign, oss.toPresignedUrl(foreign));
    }

    // 「host 与当前 public-url 不一致时仍能反解对象键」不在此测：
    // 那条路径会进入真实签名分支（需 MinIO client），而其前置的反解逻辑
    // 已由 OssUtilsTest#extractObjectNameShouldFallBackToBucketSegmentWhenBaseDiffers 干净覆盖。
}
