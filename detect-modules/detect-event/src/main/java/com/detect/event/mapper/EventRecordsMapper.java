package com.detect.event.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.detect.event.entity.EventRecords;

/**
 * 事件记录 Mapper。基础 CRUD 由 {@link BaseMapper} 提供；
 * 统计聚合、按 source_data.task 过滤等自定义查询在事件域(6b)按需追加。
 */
public interface EventRecordsMapper extends BaseMapper<EventRecords> {
}
