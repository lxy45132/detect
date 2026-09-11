package com.detect.event.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量逻辑删除请求体(接口文档 §4.1.6)：{@code {"ids":[1,2,3]}}。
 * 通用于事件/规则/通知的批量删除。
 */
@Data
public class BatchDeleteDTO {

    @NotEmpty(message = "ids 不能为空")
    private List<Long> ids;
}
