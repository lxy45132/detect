package com.detect.event.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HandleStatusEnum} 单测(6d-1)：§5.2 流转矩阵(canTransition 覆盖 16 格 + 越界/null) + 名称翻译(nameOf)。
 * 采显式 @Test 断言(项目未引入 junit-jupiter-params，对齐既有裸 @Test 约定)。
 */
class HandleStatusEnumTest {

    @Test
    void canTransition_fromPending() {
        assertTrue(HandleStatusEnum.canTransition(0, 1));   // 未处理 → 处理中 ✓
        assertTrue(HandleStatusEnum.canTransition(0, 3));   // 未处理 → 误报 ✓
        assertFalse(HandleStatusEnum.canTransition(0, 2));  // 未处理 → 已处理 ✗(须经处理中)
        assertFalse(HandleStatusEnum.canTransition(0, 0));  // 同状态 ✗
    }

    @Test
    void canTransition_fromProcessing() {
        assertTrue(HandleStatusEnum.canTransition(1, 2));   // 处理中 → 已处理 ✓
        assertTrue(HandleStatusEnum.canTransition(1, 3));   // 处理中 → 误报 ✓
        assertFalse(HandleStatusEnum.canTransition(1, 0));  // 不回退 ✗
        assertFalse(HandleStatusEnum.canTransition(1, 1));  // 同状态 ✗
    }

    @Test
    void canTransition_terminalStates_noOutgoing() {
        // 已处理(2) 终态：无出边
        assertFalse(HandleStatusEnum.canTransition(2, 0));
        assertFalse(HandleStatusEnum.canTransition(2, 1));
        assertFalse(HandleStatusEnum.canTransition(2, 3));
        assertFalse(HandleStatusEnum.canTransition(2, 2));
        // 误报(3) 终态：无出边
        assertFalse(HandleStatusEnum.canTransition(3, 0));
        assertFalse(HandleStatusEnum.canTransition(3, 1));
        assertFalse(HandleStatusEnum.canTransition(3, 2));
        assertFalse(HandleStatusEnum.canTransition(3, 3));
    }

    @Test
    void canTransition_outOfRange_false() {
        assertFalse(HandleStatusEnum.canTransition(0, 9));   // 目标越界
        assertFalse(HandleStatusEnum.canTransition(9, 1));   // 源越界
        assertFalse(HandleStatusEnum.canTransition(0, -1));  // 负值
    }

    @Test
    void canTransition_nullArgs_false() {
        assertFalse(HandleStatusEnum.canTransition(null, 1));
        assertFalse(HandleStatusEnum.canTransition(0, null));
        assertFalse(HandleStatusEnum.canTransition(null, null));
    }

    @Test
    void nameOf_translates() {
        assertEquals("未处理", HandleStatusEnum.nameOf(0));
        assertEquals("处理中", HandleStatusEnum.nameOf(1));
        assertEquals("已处理", HandleStatusEnum.nameOf(2));
        assertEquals("误报忽略", HandleStatusEnum.nameOf(3));
        assertNull(HandleStatusEnum.nameOf(9));
        assertNull(HandleStatusEnum.nameOf(null));
    }
}
