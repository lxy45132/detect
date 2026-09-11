package com.detect.auth.service;

import com.detect.auth.entity.SysUser;
import com.detect.auth.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DetectUserDetailsService 单测：验证角色映射 ROLE_{role} 与用户不存在时的异常。
 */
class DetectUserDetailsServiceTest {

    @Test
    void loadUserByUsername_found_shouldMapRoleAndUserId() {
        SysUserMapper mapper = mock(SysUserMapper.class);
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword("encoded");
        user.setRole("ADMIN");
        user.setEnabled(1);
        when(mapper.selectOne(any())).thenReturn(user);

        DetectUserDetailsService service = new DetectUserDetailsService(mapper);
        UserDetails userDetails = service.loadUserByUsername("admin");

        assertInstanceOf(DetectUserDetails.class, userDetails);
        DetectUserDetails principal = (DetectUserDetails) userDetails;
        assertEquals(1L, principal.getUserId());
        assertEquals("admin", principal.getUsername());
        assertTrue(principal.isEnabled());
        assertTrue(principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals));
    }

    @Test
    void loadUserByUsername_blankRole_shouldFallbackToUser() {
        SysUserMapper mapper = mock(SysUserMapper.class);
        SysUser user = new SysUser();
        user.setId(2L);
        user.setUsername("guest");
        user.setPassword("encoded");
        user.setRole("  ");
        user.setEnabled(null);
        when(mapper.selectOne(any())).thenReturn(user);

        DetectUserDetailsService service = new DetectUserDetailsService(mapper);
        DetectUserDetails principal = (DetectUserDetails) service.loadUserByUsername("guest");

        assertTrue(principal.isEnabled(), "enabled 为 null 时默认启用");
        assertTrue(principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_USER"::equals));
    }

    @Test
    void loadUserByUsername_notFound_shouldThrow() {
        SysUserMapper mapper = mock(SysUserMapper.class);
        when(mapper.selectOne(any())).thenReturn(null);

        DetectUserDetailsService service = new DetectUserDetailsService(mapper);
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("ghost"));
    }
}
