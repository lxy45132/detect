package com.detect.common.mybatis.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类：统一 create_time / update_time 自动填充与 del_flag 逻辑删除。
 * 业务实体继承本类即可，字段与《接口文档》附录 B 各表对齐。
 */
@Data
public class BaseEntity implements Serializable {

    /** 创建时间，插入时自动填充 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间，插入与更新时自动填充 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0 正常，1 已删除 */
    @TableLogic(value = "0", delval = "1")
    @TableField("del_flag")
    private String delFlag;
}
