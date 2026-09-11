package com.detect.event.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.detect.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 布控预警规则，对齐接口文档 §3.3。
 *
 * <p>{@code match_config}/{@code device_scope}/{@code time_scope} 为 JSON 列，以字符串存储，
 * 规则引擎(6c)按 {@code rule_type} 解析。create_time/update_time/del_flag 由 {@link BaseEntity} 提供。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("alert_rule")
public class AlertRule extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规则名称 */
    private String ruleName;
    /** 规则类型:PLATE_BLACKLIST/VEHICLE_TYPE/CROWD_THRESHOLD/DEVICE_TIME */
    private String ruleType;
    /** 适用事件大类，NULL=全部 */
    private Integer eventType;
    /** 匹配配置 JSON，如 {"crowdNum":">10"} */
    private String matchConfig;
    /** 命中后优先级:0普通 1重要 2紧急 */
    private Integer priority;
    /** 是否生成站内通知:0否 1是 */
    private Integer notifyEnabled;
    /** 生效设备范围 JSON，NULL=全部，如 ["dev01","dev02"] */
    private String deviceScope;
    /** 生效时段 JSON，NULL=不限，如 {"start":"22:00","end":"06:00"} */
    private String timeScope;
    /** 启用状态:0停用 1启用 */
    private Integer enabled;
    /** 备注 */
    private String remark;
    /** 创建人 */
    private String createBy;
}
