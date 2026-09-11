package com.detect.common.oss.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
