package com.detect.auth.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.detect.auth.entity.SysUser;
import com.detect.auth.mapper.SysUserMapper;
import com.detect.common.core.constant.CommonConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 启动时幂等播种初始管理员 admin/123456(BCrypt)。避免在 SQL 中硬编码密码哈希。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthDataInitializer implements ApplicationRunner {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        Long count = sysUserMapper.selectCount(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, "admin"));
        if (count != null && count > 0) {
            return;
        }
        SysUser admin = new SysUser();
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode("123456"));
        admin.setNickname("超级管理员");
        admin.setRole("ADMIN");
        admin.setEnabled(1);
        admin.setDelFlag(CommonConstants.DEL_FLAG_NORMAL);
        sysUserMapper.insert(admin);
        log.warn("[auth] 已播种初始管理员 admin/123456，请尽快修改密码");
    }
}
