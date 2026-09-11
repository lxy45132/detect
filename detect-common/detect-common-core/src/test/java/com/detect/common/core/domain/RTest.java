package com.detect.common.core.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RTest {

    @Test
    void okShouldHaveCodeZero() {
        R<String> r = R.ok("hi");
        assertEquals(0, r.getCode());
        assertEquals("hi", r.getData());
        assertTrue(r.isSuccess());
    }

    @Test
    void failedShouldCarryCode() {
        R<Void> r = R.failed(1001, "事件不存在");
        assertEquals(1001, r.getCode());
        assertFalse(r.isSuccess());
    }
}
