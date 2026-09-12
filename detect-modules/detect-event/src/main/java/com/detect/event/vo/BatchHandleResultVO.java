package com.detect.event.vo;

import java.util.List;

/**
 * 批量处理结果(接口文档 §4.3.3)：{@code {"processed":3,"skipped":[]}}。
 *
 * <p>{@code processed}=成功流转条数；{@code skipped}=被跳过的条目(事件不存在 1001 / 流转非法 3001)及原因。
 * 规格示例的 {@code skipped} 为空数组；本实现回报 {@code {eventId,reason}} 以便前端提示，属兼容增强。
 */
public record BatchHandleResultVO(int processed, List<Skipped> skipped) {

    /** 跳过项：事件 ID + 跳过原因(错误码文案)。 */
    public record Skipped(Long eventId, String reason) {
    }
}
