package com.detect.event.vo;

import java.util.List;

/**
 * 全量枚举字典(接口文档 §4.5.3)：前端启动时一次性加载缓存。
 * 字段顺序与规格响应一致：{@code {eventType, task, handleStatus, priority, ruleType}}。
 */
public record EventEnumsVO(List<EnumItemVO> eventType,
                           List<TaskItemVO> task,
                           List<EnumItemVO> handleStatus,
                           List<EnumItemVO> priority,
                           List<EnumItemVO> ruleType) {
}
