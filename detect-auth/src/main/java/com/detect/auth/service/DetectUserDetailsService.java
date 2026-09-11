package com.detect.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.detect.auth.entity.SysUser;
import com.detect.auth.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 从 sys_user 表加载用户(逻辑删除自动过滤 del_flag=1)。
 */
@Service
@RequiredArgsConstructor
public class DetectUserDetailsService implements UserDetailsService {

    private final SysUserMapper sysUserMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser sysUser = sysUserMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
        if (sysUser == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        String role = (sysUser.getRole() == null || sysUser.getRole().isBlank()) ? "USER" : sysUser.getRole();
        boolean enabled = sysUser.getEnabled() == null || sysUser.getEnabled() == 1;
        return new DetectUserDetails(sysUser.getId(), sysUser.getUsername(), sysUser.getPassword(),
                enabled, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
