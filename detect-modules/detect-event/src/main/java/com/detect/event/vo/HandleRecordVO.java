package com.detect.event.vo;

/**
 * 处理记录分页项(接口文档 §4.3.4)：{@code {id, eventId, fromStatus, toStatus, handlerName, handleRemark, handleTime}}。
 * {@code handleTime} 已格式化 {@code yyyy-MM-dd HH:mm:ss}(§2.4)。
 */
public record HandleRecordVO(Long id, Long eventId, Integer fromStatus, Integer toStatus,
                             String handlerName, String handleRemark, String handleTime) {
}
