package com.detect.common.core.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PageResultTest {

    @Test
    void pagesShouldBeCeilOfTotalDivSize() {
        PageResult<Integer> p = new PageResult<>(List.of(1, 2, 3), 128, 1, 20);
        assertEquals(7, p.getPages());
        assertEquals(128, p.getTotal());
        assertEquals(3, p.getRecords().size());
    }
}
