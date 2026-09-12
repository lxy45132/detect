package com.detect.event.dto;

import lombok.Data;

/**
 * 我的通知分页筛选参数(接口文档 §4.4.1)。GET 查询串绑定。
 *
 * <p>{@code user_id} 不在参数内——一律从 JWT 上下文取，用户只能查自己的通知(§4.4 前言)。
 * 服务层按 {@code create_time DESC, id DESC} 排序(最新在前)。
 */
@Data
public class NotificationQueryDTO {

    /** 页码，从 1 起 */
    private long current = 1;
    /** 每页条数，默认 20，上限 200 */
    private long size = 20;

    /** 已读标记 0未读/1已读，null=全部 */
    private Integer readFlag;
    /** 类型 alert预警/system系统，null=全部 */
    private String type;
}
