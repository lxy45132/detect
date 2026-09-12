package com.detect.event.vo;

/**
 * 事件子类字典项(接口文档 §4.5.2)：{@code {code, name, eventType}}，比 {@link EnumItemVO} 多所属大类。
 */
public record TaskItemVO(String code, String name, Integer eventType) {
}
