package com.detect.event.vo;

/**
 * 处理历史项(接口文档 §4.1.3 detail.handleHistory[])：{@code {toStatus, handlerName, handleRemark, handleTime}}。
 * {@code handleTime} 已格式化 {@code yyyy-MM-dd HH:mm:ss}。
 */
public record HandleHistoryVO(Integer toStatus, String handlerName, String handleRemark, String handleTime) {
}
