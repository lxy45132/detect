-- detect_auth 库：系统用户表
-- 说明：初始管理员 admin/123456 由应用启动时 AuthDataInitializer 幂等播种(BCrypt)，不在此硬编码密码哈希。
USE detect_auth;

CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(64)  NOT NULL COMMENT '登录名',
    password    VARCHAR(128) NOT NULL COMMENT 'BCrypt 密码',
    nickname    VARCHAR(64)           DEFAULT NULL COMMENT '昵称',
    role        VARCHAR(32)           DEFAULT 'USER' COMMENT '角色(映射为 ROLE_x)',
    enabled     TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
    del_flag    CHAR(1)      NOT NULL DEFAULT '0' COMMENT '0正常 1删除',
    create_time DATETIME              DEFAULT NULL COMMENT '创建时间',
    update_time DATETIME              DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统用户';
