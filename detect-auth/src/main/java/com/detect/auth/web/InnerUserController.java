package com.detect.auth.web;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.detect.auth.entity.SysUser;
import com.detect.auth.mapper.SysUserMapper;
import com.detect.auth.vo.AdminUserVO;
import com.detect.common.core.annotation.Inner;
import com.detect.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 内部用户接口({@link Inner}，免 JWT，须带 {@code from: Y})。供 detect-event 扇出预警通知时列出管理员。
 *
 * <p>路径 {@code /inner/**} 已在 {@link com.detect.auth.config.AuthSecurityConfig} permitAll；
 * 访问控制由 {@link com.detect.common.core.aspect.InnerAspect} 校验 {@code from:Y} 头(缺失→403)。
 */
@Slf4j
@RestController
@RequestMapping("/inner/users")
@RequiredArgsConstructor
public class InnerUserController {

    private static final String ROLE_ADMIN = "ADMIN";

    private final SysUserMapper sysUserMapper;

    /** 列出所有启用的管理员(role=ADMIN 且 enabled=1)，按 id ASC。del_flag 由 @TableLogic 自动过滤。 */
    @Inner
    @GetMapping("/admins")
    public R<List<AdminUserVO>> listAdmins() {
        List<SysUser> admins = sysUserMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getRole, ROLE_ADMIN)
                .eq(SysUser::getEnabled, 1)
                .orderByAsc(SysUser::getId));
        List<AdminUserVO> vos = admins.stream()
                .map(u -> new AdminUserVO(u.getId(), u.getUsername(), u.getNickname()))
                .toList();
        log.info("[inner] 列管理员 {} 个", vos.size());
        return R.ok(vos);
    }
}
