package com.detect.event.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 预警处理记录(审计留痕)，对齐接口文档 §3.4。
 *
 * <p>无 del_flag / create_time，故<b>不继承</b> {@code BaseEntity}(避免 @TableLogic 引用不存在的列)；
 * {@code handle_time} 由服务层显式写入(6d)。
 */
@Data
@TableName("alert_handle_record")
public class AlertHandleRecord implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联事件 ID */
    private Long eventId;
    /** 原处理状态 */
    private Integer fromStatus;
    /** 新处理状态 */
    private Integer toStatus;
    /** 处理人 ID */
    private Long handlerId;
    /** 处理人名称 */
    private String handlerName;
    /** 处理备注 */
    private String handleRemark;
    /** 处理时间 */
    private LocalDateTime handleTime;
}
