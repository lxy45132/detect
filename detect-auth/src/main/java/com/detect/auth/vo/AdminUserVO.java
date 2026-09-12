package com.detect.auth.vo;

/**
 * 管理员摘要(内部 @Inner 接口返回，供 detect-event 扇出预警通知)。仅暴露必要字段，不含 password/role。
 */
public record AdminUserVO(Long id, String username, String nickname) {
}
