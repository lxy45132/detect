package com.detect.event.vo;

/**
 * 我的通知分页项(接口文档 §4.4.1)：{@code {id, title, content, type, bizId, priority, readFlag, createTime}}。
 * {@code createTime} 已格式化 {@code yyyy-MM-dd HH:mm:ss}(§2.4)。
 */
public record NotificationVO(Long id, String title, String content, String type, Long bizId,
                             Integer priority, Integer readFlag, String createTime) {
}
