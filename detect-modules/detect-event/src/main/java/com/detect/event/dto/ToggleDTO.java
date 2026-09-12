package com.detect.event.dto;

import lombok.Data;

/**
 * 规则启用/停用请求体(接口文档 §4.2.6)。
 */
@Data
public class ToggleDTO {

    /** 目标启用状态：0 停用 / 1 启用 */
    private Integer enabled;
}
