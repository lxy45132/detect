package com.detect.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * receive 请求体，字段严格对齐 Python {@code api/schema.py} 的 {@code make_record} 输出(camelCase)。
 *
 * <p>非侵入约束：仅 {@code deviceNum/eventType/snapTime/sourceData} 为硬性必填；
 * {@code snapImage} <b>可选</b>——Python 图像编码失败会传 null，若强校验会导致其无限重试 + 落盘 spool。
 * {@code deviceName/status} 由 Java 端补全(查 camera_manage / 默认 0)，即便 Python 传值也以 Java 为准。
 */
@Data
public class EventReceiveDTO {

    /** 设备编号(Python device_id) */
    @NotBlank(message = "设备编号不能为空")
    private String deviceNum;

    /** 事件大类:100人脸 200车辆 300聚集 */
    @NotNull(message = "事件大类不能为空")
    private Integer eventType;

    /** 抓拍时间，ISO8601 带偏移(如 2026-09-10T08:49:50.123+08:00)，Jackson JavaTimeModule 解析 */
    @NotNull(message = "抓拍时间不能为空")
    private OffsetDateTime snapTime;

    /** 检测元数据 JSON 字符串，落 source_data(NOT NULL)，含 task/trackId/confidence/bbox */
    @NotBlank(message = "检测元数据不能为空")
    private String sourceData;

    /** base64 主图，可选；存在则转存 MinIO 写 snapUrl，不入库 */
    private String snapImage;

    // ---- 以下字段 Python 常传 null，Java 落 NULL 或补全 ----
    /** 设备名称：Java 按 deviceNum 查 camera_manage 补全(忽略入参) */
    private String deviceName;
    /** 预警推送状态：Java 默认 0(忽略入参) */
    private Integer status;

    private String name;
    private String cardno;
    private String libName;
    private Integer similarity;
    private String identifyFaceUrl;
    private String visibleLightUrl;
    private String plateNum;
    private Integer vehicleType;
    private String vehicleNormalType;
    private String vehicleLogo;
    private String vehicleSubLogo;
    private String vehicleColor;
    private String vehicleModel;
    private BigDecimal heightPermitted;
    private Integer crowdNum;
}
