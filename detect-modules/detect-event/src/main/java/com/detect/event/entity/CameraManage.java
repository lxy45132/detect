package com.detect.event.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.detect.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 摄像头设备(最小版)。接口文档 §3.2 提到 receive 按 {@code device_num} 查 camera_manage 补全
 * {@code device_name}，但未给出表结构；按平台约定建最小字段 + 继承 {@link BaseEntity}，并 seed 样例设备。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("camera_manage")
public class CameraManage extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 设备编号(唯一，对齐 Python device_id) */
    private String deviceNum;
    /** 设备名称 */
    private String deviceName;
    /** 安装位置 */
    private String location;
    /** 状态:1在线 0离线 */
    private Integer status;
}
