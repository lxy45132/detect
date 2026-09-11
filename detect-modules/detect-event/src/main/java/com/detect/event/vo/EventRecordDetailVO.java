package com.detect.event.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 事件详情(接口文档 §4.1.3)：完整业务字段 + {@code sourceData} 解析对象 + 命中规则摘要 + 处理历史。
 *
 * <p>{@code sourceData} 类型为 {@link Object}：服务层将库中 JSON 字符串解析为 Map 后回填，
 * Jackson 直接序列化(解析失败则回退原始字符串)。{@code snapTime} 已格式化 {@code yyyy-MM-dd HH:mm:ss}。
 */
@Data
public class EventRecordDetailVO {

    private Long id;
    private String deviceNum;
    private String deviceName;
    private Integer eventType;
    private String eventTypeName;
    private String snapTime;
    private String snapUrl;

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

    private Integer status;
    private Integer handleStatus;
    private Integer priority;
    private Long hitRuleId;

    /** source_data 解析后的对象(含 task/confidence/trackId/bbox 等) */
    private Object sourceData;
    /** 命中规则摘要，未命中为 null */
    private HitRuleVO hitRule;
    /** 处理历史(按时间升序)，无记录为空列表 */
    private List<HandleHistoryVO> handleHistory;
}
