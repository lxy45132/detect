package com.detect.common.core.domain;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页响应结构：{records, total, current, size, pages}，对齐接口文档 §2.3。
 *
 * @param <T> 记录类型
 */
@Data
public class PageResult<T> implements Serializable {

    private List<T> records = Collections.emptyList();
    private long total;
    private long current;
    private long size;
    private long pages;

    public PageResult() {
    }

    public PageResult(List<T> records, long total, long current, long size) {
        this.records = records == null ? Collections.emptyList() : records;
        this.total = total;
        this.current = current;
        this.size = size;
        this.pages = size <= 0 ? 0 : (total + size - 1) / size;
    }
}
