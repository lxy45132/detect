package com.detect.event.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.detect.event.entity.CameraManage;

/**
 * 摄像头设备 Mapper。基础 CRUD 由 {@link BaseMapper} 提供；
 * receive 按 device_num 查设备名走 {@code selectOne}。
 */
public interface CameraManageMapper extends BaseMapper<CameraManage> {
}
