package com.detect.event.vo;

/**
 * 全部标记已读结果(接口文档 §4.4.4)：{@code {"read": N}}，N 为本次由未读转已读的条数。
 */
public record ReadAllVO(long read) {
}
