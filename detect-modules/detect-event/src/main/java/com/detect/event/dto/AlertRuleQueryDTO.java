package com.detect.event.dto;

import lombok.Data;

/**
 * 规则分页筛选参数(接口文档 §4.2.1)。GET 查询串绑定，全部可选。
 */
@Data
public class AlertRuleQueryDTO {

    /** 页码，从 1 起 */
    private long current = 1;
    /** 每页条数，默认 20，上限 200 */
    private long size = 20;

    /** 规则类型(精确)：PLATE_BLACKLIST/VEHICLE_TYPE/CROWD_THRESHOLD/DEVICE_TIME */
    private String ruleType;
    /** 启用状态：0 停用 / 1 启用 */
    private Integer enabled;
    /** 关键词(规则名模糊) */
    private String keyword;
}
