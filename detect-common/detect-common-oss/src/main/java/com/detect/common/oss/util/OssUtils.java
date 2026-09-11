package com.detect.common.oss.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;

/**
 * OSS 纯函数工具：对象键生成、可访问 URL 拼接、base64 解码。不依赖 MinIO，便于单测。
 */
public final class OssUtils {

    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyyMMdd");

    private OssUtils() {
    }

    /** 生成对象键：{dir}/{yyyyMMdd}/{uuid32}[.{ext}] */
    public static String generateObjectKey(String dir, String originalFilename) {
        String ext = extractExt(originalFilename);
        String dateDir = LocalDate.now().format(DATE_DIR);
        String base = (dir == null || dir.isBlank()) ? "files" : trimSlash(dir);
        String name = UUID.randomUUID().toString().replace("-", "");
        String prefix = base + "/" + dateDir + "/" + name;
        return ext.isEmpty() ? prefix : prefix + "." + ext;
    }

    /** 拼接可访问 URL：{publicBase}/{bucket}/{objectName}，规避重复斜杠 */
    public static String buildUrl(String publicBase, String bucket, String objectName) {
        String base = trimSlash(publicBase == null ? "" : publicBase);
        String b = trimSlash(bucket == null ? "" : bucket);
        String o = objectName == null ? "" : trimLeadingSlash(objectName);
        StringBuilder sb = new StringBuilder(base);
        if (!b.isEmpty()) {
            sb.append("/").append(b);
        }
        if (!o.isEmpty()) {
            sb.append("/").append(o);
        }
        return sb.toString();
    }

    /** 解码 base64，自动剥离 data URI 前缀(data:image/png;base64,)与空白字符 */
    public static byte[] decodeBase64(String base64) {
        if (base64 == null) {
            return new byte[0];
        }
        String data = base64;
        int comma = data.indexOf(',');
        if (data.startsWith("data:") && comma > 0) {
            data = data.substring(comma + 1);
        }
        return Base64.getDecoder().decode(data.replaceAll("\\s", ""));
    }

    private static String extractExt(String filename) {
        if (filename == null) {
            return "";
        }
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) {
            return "";
        }
        return filename.substring(idx + 1).toLowerCase();
    }

    private static String trimSlash(String s) {
        String r = s;
        while (r.startsWith("/")) {
            r = r.substring(1);
        }
        while (r.endsWith("/")) {
            r = r.substring(0, r.length() - 1);
        }
        return r;
    }

    private static String trimLeadingSlash(String s) {
        String r = s;
        while (r.startsWith("/")) {
            r = r.substring(1);
        }
        return r;
    }
}
