package com.detect.event.service;

import com.detect.common.oss.OssTemplate;
import com.detect.event.dto.EventReceiveDTO;
import com.detect.event.entity.CameraManage;
import com.detect.event.entity.EventRecords;
import com.detect.event.mapper.CameraManageMapper;
import com.detect.event.mapper.EventRecordsMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link EventRecordService#receive} 单测：派生字段补全、东八区归一化、幂等去重(含 trackId 区分)、无图跳过 MinIO。
 * 用 Mockito 隔离 Mapper 与 OssTemplate，不依赖真实 DB/MinIO。
 */
@ExtendWith(MockitoExtension.class)
class EventRecordServiceTest {

    @Mock
    private EventRecordsMapper eventRecordsMapper;
    @Mock
    private CameraManageMapper cameraManageMapper;
    @Mock
    private OssTemplate ossTemplate;

    @InjectMocks
    private EventRecordService service;

    /** UTC 00:49:50.123Z → 东八区应为 08:49:50.123；trackId=12。 */
    private EventReceiveDTO baseDto() {
        EventReceiveDTO dto = new EventReceiveDTO();
        dto.setDeviceNum("dev01");
        dto.setEventType(200);
        dto.setSnapTime(OffsetDateTime.parse("2026-09-10T00:49:50.123Z"));
        dto.setSourceData("{\"task\":\"vehicle_type\",\"trackId\":12}");
        dto.setSnapImage("BASE64IMG");
        dto.setVehicleNormalType("SLAGTRUCK");
        dto.setVehicleType(3);
        dto.setSimilarity(87);
        return dto;
    }

    private void stubInsertAssignsId(long id) {
        when(eventRecordsMapper.insert(any())).thenAnswer(inv -> {
            ((EventRecords) inv.getArgument(0)).setId(id);
            return 1;
        });
    }

    @Test
    void receive_happyPath_fillsDerivedFieldsAndInserts() {
        when(eventRecordsMapper.selectList(any())).thenReturn(List.of());
        CameraManage cam = new CameraManage();
        cam.setDeviceNum("dev01");
        cam.setDeviceName("南河湫水闸");
        when(cameraManageMapper.selectOne(any())).thenReturn(cam);
        when(ossTemplate.uploadBase64(eq("BASE64IMG"), eq("event"), anyString(), anyString()))
                .thenReturn("http://localhost:9000/detect/event/20260910/x.jpg");
        stubInsertAssignsId(10086L);

        Long id = service.receive(baseDto());

        assertEquals(10086L, id);
        ArgumentCaptor<EventRecords> cap = ArgumentCaptor.forClass(EventRecords.class);
        verify(eventRecordsMapper).insert(cap.capture());
        EventRecords saved = cap.getValue();
        assertEquals("dev01", saved.getDeviceNum());
        assertEquals("南河湫水闸", saved.getDeviceName());
        assertEquals(0, saved.getStatus().intValue());
        assertEquals("http://localhost:9000/detect/event/20260910/x.jpg", saved.getSnapUrl());
        assertEquals(0, saved.getHandleStatus().intValue());
        assertEquals(0, saved.getPriority().intValue());
        assertEquals("SLAGTRUCK", saved.getVehicleNormalType());
        assertEquals(3, saved.getVehicleType().intValue());
        assertEquals(LocalDateTime.of(2026, 9, 10, 8, 49, 50, 123_000_000), saved.getSnapTime());
    }

    @Test
    void receive_idempotent_returnsExistingIdWithoutInsertOrOss() {
        EventRecords dup = new EventRecords();
        dup.setId(555L);
        dup.setSourceData("{\"task\":\"vehicle_type\",\"trackId\":12}");
        when(eventRecordsMapper.selectList(any())).thenReturn(List.of(dup));

        Long id = service.receive(baseDto());

        assertEquals(555L, id);
        verify(eventRecordsMapper, never()).insert(any());
        verify(ossTemplate, never()).uploadBase64(any(), any(), any(), any());
        verify(cameraManageMapper, never()).selectOne(any());
    }

    @Test
    void receive_sameDeviceAndTimeButDifferentTrackId_isNotDuplicate() {
        EventRecords other = new EventRecords();
        other.setId(777L);
        other.setSourceData("{\"task\":\"vehicle_type\",\"trackId\":99}");
        when(eventRecordsMapper.selectList(any())).thenReturn(List.of(other));
        when(ossTemplate.uploadBase64(any(), any(), any(), any())).thenReturn("u");
        stubInsertAssignsId(10087L);

        Long id = service.receive(baseDto());

        assertEquals(10087L, id);
        verify(eventRecordsMapper).insert(any());
    }

    @Test
    void receive_nullSnapImage_skipsMinioAndLeavesSnapUrlNull() {
        EventReceiveDTO dto = baseDto();
        dto.setSnapImage(null);
        when(eventRecordsMapper.selectList(any())).thenReturn(List.of());
        stubInsertAssignsId(1L);

        service.receive(dto);

        verify(ossTemplate, never()).uploadBase64(any(), any(), any(), any());
        ArgumentCaptor<EventRecords> cap = ArgumentCaptor.forClass(EventRecords.class);
        verify(eventRecordsMapper).insert(cap.capture());
        assertNull(cap.getValue().getSnapUrl());
        assertNull(cap.getValue().getDeviceName());
    }
}
