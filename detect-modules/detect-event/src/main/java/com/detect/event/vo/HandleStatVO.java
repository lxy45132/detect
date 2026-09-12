package com.detect.event.vo;

/**
 * 处理效率统计(接口文档 §4.3.6)：{@code {pendingCount, processingCount, todayResolved, falseRate, avgHandleMinutes}}。
 *
 * <p>公式为本期约定(规格仅给字段名，未定口径)：
 * <ul>
 *   <li>{@code pendingCount}/{@code processingCount}：事件 handle_status=0/1 计数(受 deviceNum + snap_time 区间过滤)</li>
 *   <li>{@code falseRate}：误报(handle_status=3)/总数，0~1 两位小数，总数为 0 时取 0</li>
 *   <li>{@code todayResolved}：今日(handle_time ≥ 当日 00:00) to_status=2 的处理记录数(不受 deviceNum/区间过滤，"今日"为字段固有语义)</li>
 *   <li>{@code avgHandleMinutes}：已解决事件 snap_time → 解决 handle_time 的平均分钟，一位小数，无样本取 0</li>
 * </ul>
 */
public record HandleStatVO(long pendingCount, long processingCount, long todayResolved,
                           double falseRate, double avgHandleMinutes) {
}
