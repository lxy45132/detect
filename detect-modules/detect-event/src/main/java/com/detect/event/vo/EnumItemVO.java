package com.detect.event.vo;

/**
 * 通用枚举字典项(接口文档 §4.5)：{@code {code, name}}。
 *
 * <p>{@code code} 用 {@link Object} 以兼容数值型(eventType/handleStatus/priority)与字符串型(ruleType)——
 * Jackson 分别序列化为 JSON number / string，与规格响应一致。
 */
public record EnumItemVO(Object code, String name) {
}
