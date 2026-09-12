package com.detect.event.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 批量处理请求体(接口文档 §4.3.3)：{@code {"eventIds":[1,2,3],"toStatus":3,"remark":"批量标记误报"}}。
 *
 * <p>逐条校验流转合法性，非法的跳过并在响应 {@code skipped} 中回报(不整批回滚)。
 * 处理人同 §4.3.2 从 JWT 上下文取。
 */
@Data
public class BatchHandleDTO {

    /** 目标事件 ID 列表 */
    @NotEmpty(message = "eventIds 不能为空")
    private List<Long> eventIds;

    /** 目标处理状态 0/1/2/3 */
    @NotNull(message = "toStatus 不能为空")
    private Integer toStatus;

    /** 处理备注(可选，应用于每条) */
    private String remark;
}
