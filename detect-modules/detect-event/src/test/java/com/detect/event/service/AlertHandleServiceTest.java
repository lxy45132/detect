package com.detect.event.service;

import com.detect.common.core.enums.ResultCode;
import com.detect.common.core.exception.BizException;
import com.detect.common.security.util.SecurityUtils;
import com.detect.event.dto.BatchHandleDTO;
import com.detect.event.dto.HandleProcessDTO;
import com.detect.event.entity.AlertHandleRecord;
import com.detect.event.entity.EventRecords;
import com.detect.event.mapper.AlertHandleRecordMapper;
import com.detect.event.mapper.EventRecordsMapper;
import com.detect.event.vo.BatchHandleResultVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AlertHandleService} 单测(6d-1)：合法流转更新 handle_status + 写留痕(处理人取 JWT 上下文)；
 * 事件不存在报 1001、非法流转报 3001 且零写入；批量部分成功(非法跳过并回报 processed/skipped)。
 *
 * <p>{@link SecurityUtils} 为静态方法，用 Mockito 5 {@code mockStatic} 桩处理人；校验类异常在取处理人之前抛出，
 * 故 1001/3001 用例无需开 mockStatic。Mock 两个 Mapper 隔离 DB。
 */
@ExtendWith(MockitoExtension.class)
class AlertHandleServiceTest {

    @Mock
    private EventRecordsMapper eventRecordsMapper;
    @Mock
    private AlertHandleRecordMapper alertHandleRecordMapper;

    @InjectMocks
    private AlertHandleService service;

    private EventRecords event(long id, int handleStatus) {
        EventRecords e = new EventRecords();
        e.setId(id);
        e.setHandleStatus(handleStatus);
        return e;
    }

    private HandleProcessDTO dto(long eventId, int toStatus, String remark) {
        HandleProcessDTO d = new HandleProcessDTO();
        d.setEventId(eventId);
        d.setToStatus(toStatus);
        d.setRemark(remark);
        return d;
    }

    @Test
    void process_legalTransition_updatesStatusAndWritesRecord() {
        when(eventRecordsMapper.selectById(7L)).thenReturn(event(7L, 0));  // 未处理

        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::getUserId).thenReturn(1L);
            ms.when(SecurityUtils::getUsername).thenReturn("admin");
            service.process(dto(7L, 1, "开始处理"));   // 0 → 1 合法
        }

        // 更新 handle_status=1
        ArgumentCaptor<EventRecords> upd = ArgumentCaptor.forClass(EventRecords.class);
        verify(eventRecordsMapper).updateById(upd.capture());
        assertEquals(7L, upd.getValue().getId());
        assertEquals(1, upd.getValue().getHandleStatus().intValue());

        // 写留痕：from=0,to=1,处理人取自 JWT,备注透传,handle_time 非空
        ArgumentCaptor<AlertHandleRecord> hr = ArgumentCaptor.forClass(AlertHandleRecord.class);
        verify(alertHandleRecordMapper).insert(hr.capture());
        AlertHandleRecord rec = hr.getValue();
        assertEquals(7L, rec.getEventId());
        assertEquals(0, rec.getFromStatus().intValue());
        assertEquals(1, rec.getToStatus().intValue());
        assertEquals(1L, rec.getHandlerId());
        assertEquals("admin", rec.getHandlerName());
        assertEquals("开始处理", rec.getHandleRemark());
        assertNotNull(rec.getHandleTime());
    }

    @Test
    void process_eventNotFound_throws1001_noWrite() {
        when(eventRecordsMapper.selectById(999L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.process(dto(999L, 1, "x")));

        assertEquals(ResultCode.EVENT_NOT_FOUND.getCode(), ex.getCode());
        verify(eventRecordsMapper, never()).updateById(any());
        verify(alertHandleRecordMapper, never()).insert(any());
    }

    @Test
    void process_illegalTransition_throws3001_noWrite() {
        when(eventRecordsMapper.selectById(7L)).thenReturn(event(7L, 2));  // 已处理(终态)

        BizException ex = assertThrows(BizException.class, () -> service.process(dto(7L, 1, "回退")));  // 2 → 1 非法

        assertEquals(ResultCode.STATUS_TRANSITION_INVALID.getCode(), ex.getCode());
        verify(eventRecordsMapper, never()).updateById(any());
        verify(alertHandleRecordMapper, never()).insert(any());
    }

    @Test
    void batchProcess_mixedLegalIllegal_partialSuccess() {
        // id=1 未处理(0→3 合法)、id=2 已处理(终态→3 非法)、id=3 不存在
        when(eventRecordsMapper.selectById(1L)).thenReturn(event(1L, 0));
        when(eventRecordsMapper.selectById(2L)).thenReturn(event(2L, 2));
        when(eventRecordsMapper.selectById(3L)).thenReturn(null);

        BatchHandleDTO batch = new BatchHandleDTO();
        batch.setEventIds(List.of(1L, 2L, 3L));
        batch.setToStatus(3);
        batch.setRemark("批量标记误报");

        BatchHandleResultVO result;
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::getUserId).thenReturn(1L);
            ms.when(SecurityUtils::getUsername).thenReturn("admin");
            result = service.batchProcess(batch);
        }

        assertEquals(1, result.processed());        // 仅 id=1 成功
        assertEquals(2, result.skipped().size());   // id=2(3001) + id=3(1001)
        verify(eventRecordsMapper, times(1)).updateById(any());
        verify(alertHandleRecordMapper, times(1)).insert(any());
    }

    @Test
    void batchProcess_allLegal_allProcessed() {
        when(eventRecordsMapper.selectById(any())).thenReturn(event(1L, 0));  // 任意 id 均返 未处理

        BatchHandleDTO batch = new BatchHandleDTO();
        batch.setEventIds(List.of(1L, 2L));
        batch.setToStatus(1);
        batch.setRemark("批量受理");

        BatchHandleResultVO result;
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::getUserId).thenReturn(1L);
            ms.when(SecurityUtils::getUsername).thenReturn("admin");
            result = service.batchProcess(batch);
        }

        assertEquals(2, result.processed());
        assertTrue(result.skipped().isEmpty());
        verify(alertHandleRecordMapper, times(2)).insert(any());
    }
}
