package com.detect.event.dto;

import lombok.Data;

/**
 * 事件分页/导出筛选参数(接口文档 §4.1.2、§4.1.8)。GET 查询串绑定，全部可选。
 *
 * <p>{@code current/size} 有默认值(1/20)，服务层再夹取 size∈[1,200](§2.3)。
 * {@code task} 存于 {@code source_data} JSON 内，服务层用 MySQL JSON 函数过滤。
 */
@Data
public class EventQueryDTO {

    /** 页码，从 1 起 */
    private long current = 1;
    /** 每页条数，默认 20，上限 200 */
    private long size = 20;

    /** 设备编号(精确) */
    private String deviceNum;
    /** 事件大类 100/200/300 */
    private Integer eventType;
    /** 事件子类(source_data.task，如 license_plate) */
    private String task;
    /** 处理状态 0/1/2/3 */
    private Integer handleStatus;
    /** 优先级 0/1/2 */
    private Integer priority;
    /** 车牌(模糊) */
    private String plateNum;
    /** 关键词(车牌 / 设备名 模糊) */
    private String keyword;
    /** snap_time 区间起(yyyy-MM-dd HH:mm:ss) */
    private String startTime;
    /** snap_time 区间止(yyyy-MM-dd HH:mm:ss) */
    private String endTime;
}
