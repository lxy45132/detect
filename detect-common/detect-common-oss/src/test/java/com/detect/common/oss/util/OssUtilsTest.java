package com.detect.common.oss.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OssUtilsTest {

    @Test
    void generateObjectKeyShouldContainDirDateAndLowerExt() {
        String key = OssUtils.generateObjectKey("snap", "photo.PNG");
        assertTrue(key.startsWith("snap/"), key);
        assertTrue(key.endsWith(".png"), key);
        assertEquals(3, key.split("/").length, key); // snap/yyyyMMdd/uuid.png
    }

    @Test
    void generateObjectKeyShouldHandleNoExtension() {
        String key = OssUtils.generateObjectKey("files", "README");
        assertTrue(key.startsWith("files/"), key);
        assertFalse(key.endsWith("."), key);
    }

    @Test
    void buildUrlShouldJoinWithoutDoubleSlash() {
        String url = OssUtils.buildUrl("http://localhost:9000/", "detect", "/snap/a.png");
        assertEquals("http://localhost:9000/detect/snap/a.png", url);
    }

    @Test
    void decodeBase64ShouldStripDataUriPrefixAndWhitespace() {
        String raw = "hello";
        String b64 = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(raw.getBytes(StandardCharsets.UTF_8), OssUtils.decodeBase64(b64));
        assertArrayEquals(raw.getBytes(StandardCharsets.UTF_8),
                OssUtils.decodeBase64("data:text/plain;base64," + b64));
    }

    @Test
    void extractObjectNameShouldStripBaseAndBucket() {
        String key = OssUtils.extractObjectName(
                "http://localhost:9000/detect/event/20260911/x.jpg", "http://localhost:9000", "detect");
        assertEquals("event/20260911/x.jpg", key);
    }

    @Test
    void extractObjectNameShouldTolerateTrailingSlashInBase() {
        String key = OssUtils.extractObjectName(
                "http://localhost:9000/detect/event/a.jpg", "http://localhost:9000/", "detect");
        assertEquals("event/a.jpg", key);
    }

    @Test
    void extractObjectNameShouldRoundTripWithBuildUrl() {
        String objectName = "event/20260912/05d3801bfb5349beb1dab38b8ec95b54.jpg";
        String url = OssUtils.buildUrl("http://localhost:9000", "detect", objectName);
        assertEquals(objectName, OssUtils.extractObjectName(url, "http://localhost:9000", "detect"));
    }

    /**
     * public-url 改过域名后，库里存的历史 URL 与当前配置不一致 ——
     * 必须能靠 "/{bucket}/" 退化定位，否则老数据全部无法预签名。
     */
    @Test
    void extractObjectNameShouldFallBackToBucketSegmentWhenBaseDiffers() {
        String key = OssUtils.extractObjectName(
                "http://minio.internal:9000/detect/event/a.jpg", "http://localhost:9000", "detect");
        assertEquals("event/a.jpg", key);
    }

    @Test
    void extractObjectNameShouldNotBeFooledByBucketNameInsideObjectKey() {
        String key = OssUtils.extractObjectName(
                "http://localhost:9000/detect/detect/a.jpg", "http://localhost:9000", "detect");
        assertEquals("detect/a.jpg", key);
    }

    @Test
    void extractObjectNameShouldReturnNullForBlankOrForeignUrl() {
        assertNull(OssUtils.extractObjectName(null, "http://localhost:9000", "detect"));
        assertNull(OssUtils.extractObjectName("  ", "http://localhost:9000", "detect"));
        // 不是本桶的 URL（例如人脸库供应商的外链）：返 null，调用方据此原样返回
        assertNull(OssUtils.extractObjectName("https://vendor.com/foo/a.jpg", "http://localhost:9000", "detect"));
    }
}
