package com.detect.event.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.detect.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 事件记录主表，对齐接口文档 §3.2。
 *
 * <p>增仅来自 Python webhook 推送(receive)，前端只查/改/删。
 * {@code source_data} 为 JSON 列，以字符串存储，服务层按需解析(含 task/confidence/bbox/trackId)。
 * create_time/update_time/del_flag 由 {@link BaseEntity} 提供并自动填充/逻辑删除。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("event_records")
public class EventRecords extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 设备编号(Python device_id) */
    private String deviceNum;
    /** 设备名称(查 camera_manage 补全) */
    private String deviceName;
    /** 事件大类:100人脸 200车辆 300聚集 */
    private Integer eventType;
    /** 抓拍时间(毫秒) */
    private LocalDateTime snapTime;
    /** 主图 MinIO URL(由 snapImage base64 转存) */
    private String snapUrl;
    /** 检测元数据 JSON 字符串(不含图片) */
    private String sourceData;
    /** 预警推送状态:0未推送 */
    private Integer status;

    /** 姓名(人脸库比对) */
    private String name;
    /** 身份证号 */
    private String cardno;
    /** 车辆库/人脸库名称 */
    private String libName;
    /** 相似度 0~100 */
    private Integer similarity;
    /** 库底图 URL */
    private String identifyFaceUrl;
    /** 热成像可见光图 URL */
    private String visibleLightUrl;

    /** 车牌号 */
    private String plateNum;
    /** 特殊车辆类别 code(VehicleTypeEnum) */
    private Integer vehicleType;
    /** 车辆类别枚举名(VehicleNormalTypeEnum) */
    private String vehicleNormalType;
    /** 车辆品牌(VLM 补全) */
    private String vehicleLogo;
    /** 车辆子品牌(VLM 补全) */
    private String vehicleSubLogo;
    /** 车身颜色(VLM 补全) */
    private String vehicleColor;
    /** 车辆年款(VLM 补全) */
    private String vehicleModel;
    /** 限高(米) */
    private BigDecimal heightPermitted;
    /** 聚集人数 */
    private Integer crowdNum;

    /** 处理状态:0未处理 1处理中 2已处理 3误报 */
    private Integer handleStatus;
    /** 优先级:0普通 1重要 2紧急 */
    private Integer priority;
    /** 命中的布控规则 ID */
    private Long hitRuleId;
}
