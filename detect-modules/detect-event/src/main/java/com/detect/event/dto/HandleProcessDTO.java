package com.detect.event.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 处理单条事件请求体(接口文档 §4.3.2)：{@code {"eventId":10086,"toStatus":2,"remark":"..."}}。
 *
 * <p>处理人(handlerId/handlerName)不由前端传入，服务层从 JWT 上下文取({@code SecurityUtils})，防越权伪造。
 * {@code toStatus} 合法性由 §5.2 矩阵校验，非法报 {@code 3001}(故此处不加 @Min/@Max，统一由状态机裁决)。
 */
@Data
public class HandleProcessDTO {

    /** 目标事件 ID */
    @NotNull(message = "eventId 不能为空")
    private Long eventId;

    /** 目标处理状态 0/1/2/3 */
    @NotNull(message = "toStatus 不能为空")
    private Integer toStatus;

    /** 处理备注(可选，最长 500) */
    private String remark;
}
