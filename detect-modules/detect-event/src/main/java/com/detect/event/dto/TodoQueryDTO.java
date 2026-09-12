package com.detect.event.dto;

import lombok.Data;

/**
 * 待办列表筛选参数(接口文档 §4.3.1)。GET 查询串绑定，全部可选。
 *
 * <p>服务层固定追加 {@code handle_status ∈ {0,1}}(未处理/处理中)并按 {@code priority DESC, snap_time DESC} 排序(紧急置顶)。
 */
@Data
public class TodoQueryDTO {

    /** 页码，从 1 起 */
    private long current = 1;
    /** 每页条数，默认 20，上限 200 */
    private long size = 20;

    /** 优先级 0/1/2 */
    private Integer priority;
    /** 事件大类 100/200/300 */
    private Integer eventType;
    /** 设备编号(精确) */
    private String deviceNum;
    /** snap_time 区间起(yyyy-MM-dd HH:mm:ss) */
    private String startTime;
    /** snap_time 区间止(yyyy-MM-dd HH:mm:ss) */
    private String endTime;
}
