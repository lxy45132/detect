package com.detect.event.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 站内通知，对齐接口文档 §3.5。
 *
 * <p>无 del_flag / update_time，故<b>不继承</b> {@code BaseEntity}；删除为物理删(§4.4.5)。
 * {@code create_time} 自带 {@link FieldFill#INSERT} 以复用公共 MetaObjectHandler 自动填充。
 */
@Data
@TableName("sys_notification")
public class SysNotification implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收人 ID */
    private Long userId;
    /** 标题 */
    private String title;
    /** 内容 */
    private String content;
    /** 类型:alert预警 system系统 */
    private String type;
    /** 关联业务 ID(如 event_id) */
    private Long bizId;
    /** 优先级:0普通 1重要 2紧急 */
    private Integer priority;
    /** 已读:0未读 1已读 */
    private Integer readFlag;
    /** 已读时间 */
    private LocalDateTime readTime;
    /** 创建时间，插入自动填充 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
