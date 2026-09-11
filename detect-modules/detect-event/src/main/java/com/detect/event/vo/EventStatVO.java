package com.detect.event.vo;

import java.util.List;

/**
 * 分类统计响应(接口文档 §4.1.7)：一次性返回按大类/子类/日期的聚合计数，供看板使用。
 *
 * <p>三种计数形态内聚为嵌套 record：{@code byEventType}{code,name,count}、
 * {@code byTask}{task,name,count}、{@code byDay}{date,count}。
 */
public record EventStatVO(long total,
                          List<EventTypeCount> byEventType,
                          List<TaskCount> byTask,
                          List<DayCount> byDay) {

    /** 按事件大类聚合：code=100/200/300，name=中文名。 */
    public record EventTypeCount(Integer code, String name, Long count) {
    }

    /** 按子类聚合：task=source_data.task，name=中文名(枚举翻译，未知为 null)。 */
    public record TaskCount(String task, String name, Long count) {
    }

    /** 按日期聚合：date=yyyy-MM-dd。 */
    public record DayCount(String date, Long count) {
    }
}
