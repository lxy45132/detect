package com.detect.event.dto;

import lombok.Data;

/**
 * auth @Inner 接口返回的管理员摘要(Feign 反序列化目标)。字段对齐 auth 的 {@code AdminUserVO}。
 * 用 @Data(带无参构造 + setter)确保 Jackson 反序列化稳妥，不依赖 -parameters。
 */
@Data
public class AdminUser {

    private Long id;
    private String username;
    private String nickname;
}
