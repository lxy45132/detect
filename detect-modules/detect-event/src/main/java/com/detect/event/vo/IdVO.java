package com.detect.event.vo;

/**
 * 仅含 id 的响应体，用于 receive / 规则新增等返回 {@code {"id": x}} 的场景(接口文档 §4.1.1、§4.2.3)。
 */
public record IdVO(Long id) {
}
