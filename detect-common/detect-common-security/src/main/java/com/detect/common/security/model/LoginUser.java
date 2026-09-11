package com.detect.common.security.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 当前登录用户模型：由 JWT 声明解析而来，供业务侧做操作留痕(如处理人)。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser implements Serializable {

    /** 用户 ID，来自 token 声明 user_id */
    private Long userId;

    /** 用户名，来自 token 声明 username(缺省取 sub) */
    private String username;

    /** 权限/角色列表，来自 token 声明 authorities(如 ROLE_ADMIN) */
    private List<String> authorities;
}
