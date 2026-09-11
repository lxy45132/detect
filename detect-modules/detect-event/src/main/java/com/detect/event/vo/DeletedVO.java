package com.detect.event.vo;

/**
 * 批量逻辑删除结果(接口文档 §4.1.6)：{@code {"deleted": N}}，N 为实际受影响行数。
 */
public record DeletedVO(int deleted) {
}
