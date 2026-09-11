package com.detect.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.detect.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统用户(detect_auth.sys_user)。继承 BaseEntity 复用 create_time/update_time/del_flag。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录名 */
    private String username;

    /** BCrypt 密码 */
    private String password;

    /** 昵称 */
    private String nickname;

    /** 角色，映射为权限 ROLE_{role} */
    private String role;

    /** 是否启用：1 启用，0 禁用 */
    private Integer enabled;
}
