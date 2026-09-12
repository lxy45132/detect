package com.detect.event.dto;

import lombok.Data;

/**
 * 处理记录分页筛选参数(接口文档 §4.3.4)。GET 查询串绑定，全部可选。
 *
 * <p>按 {@code handle_time DESC, id DESC} 排序(最新处理在前)。
 */
@Data
public class HandleRecordQueryDTO {

    /** 页码，从 1 起 */
    private long current = 1;
    /** 每页条数，默认 20，上限 200 */
    private long size = 20;

    /** 事件 ID(精确) */
    private Long eventId;
    /** 处理人 ID(精确) */
    private Long handlerId;
    /** handle_time 区间起(yyyy-MM-dd HH:mm:ss) */
    private String startTime;
    /** handle_time 区间止(yyyy-MM-dd HH:mm:ss) */
    private String endTime;
}
