package com.detect.event.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 事件导出行模型(接口文档 §4.1.8)，EasyExcel 按 {@code @ExcelProperty} 生成表头。
 *
 * <p>显式 {@code index} 锁定列序(不依赖反射字段顺序)；枚举/时间列以中文名与格式化字符串直出，
 * 便于业务人员在 Excel/CSV 中直接阅读。
 */
@Data
@ColumnWidth(18)
public class EventExportRow {

    @ExcelProperty(value = "事件ID", index = 0)
    private Long id;

    @ExcelProperty(value = "设备编号", index = 1)
    private String deviceNum;

    @ExcelProperty(value = "设备名称", index = 2)
    private String deviceName;

    @ExcelProperty(value = "事件大类", index = 3)
    private String eventTypeName;

    @ExcelProperty(value = "事件子类", index = 4)
    private String task;

    @ExcelProperty(value = "抓拍时间", index = 5)
    @ColumnWidth(22)
    private String snapTime;

    @ExcelProperty(value = "车牌号", index = 6)
    private String plateNum;

    @ExcelProperty(value = "车辆类别", index = 7)
    private String vehicleNormalType;

    @ExcelProperty(value = "聚集人数", index = 8)
    private Integer crowdNum;

    @ExcelProperty(value = "处理状态", index = 9)
    private String handleStatusName;

    @ExcelProperty(value = "优先级", index = 10)
    private String priorityName;

    @ExcelProperty(value = "命中规则ID", index = 11)
    private Long hitRuleId;
}
