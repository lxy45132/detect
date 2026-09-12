package com.detect.event.vo;

/**
 * 未读通知数(接口文档 §4.4.2)：{@code {"count": N}}，供前端红点。
 */
public record UnreadCountVO(long count) {
}
