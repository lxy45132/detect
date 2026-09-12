package com.detect.auth.web;

import com.detect.auth.entity.SysUser;
import com.detect.auth.mapper.SysUserMapper;
import com.detect.auth.vo.AdminUserVO;
import com.detect.common.core.domain.R;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link InnerUserController} 单测(6c-3)：列管理员映射为 VO(仅 id/username/nickname，不含 password)、空结果。
 * 沿用 auth 既有裸 mock + 直接构造控制器风格(见 {@code OAuth2ControllerTest})。
 */
class InnerUserControllerTest {

    private SysUser user(long id, String username, String nickname, String role) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setUsername(username);
        u.setNickname(nickname);
        u.setPassword("BCRYPT-HASH");
        u.setRole(role);
        u.setEnabled(1);
        return u;
    }

    @Test
    void listAdmins_mapsToVO_excludesPassword() {
        SysUserMapper mapper = mock(SysUserMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(
                user(1L, "admin", "超级管理员", "ADMIN"),
                user(2L, "op", "操作员", "ADMIN")));
        InnerUserController controller = new InnerUserController(mapper);

        R<List<AdminUserVO>> resp = controller.listAdmins();

        assertTrue(resp.isSuccess());
        assertEquals(2, resp.getData().size());
        AdminUserVO first = resp.getData().get(0);
        assertEquals(1L, first.id());
        assertEquals("admin", first.username());
        assertEquals("超级管理员", first.nickname());
    }

    @Test
    void listAdmins_empty_returnsEmptyList() {
        SysUserMapper mapper = mock(SysUserMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());
        InnerUserController controller = new InnerUserController(mapper);

        R<List<AdminUserVO>> resp = controller.listAdmins();

        assertTrue(resp.isSuccess());
        assertTrue(resp.getData().isEmpty());
    }
}
