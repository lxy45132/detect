package com.detect.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.detect.auth.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * sys_user 数据访问。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
