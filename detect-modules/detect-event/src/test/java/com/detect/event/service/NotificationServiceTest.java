package com.detect.event.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.detect.common.core.domain.PageResult;
import com.detect.common.core.domain.R;
import com.detect.common.core.enums.ResultCode;
import com.detect.common.core.exception.BizException;
import com.detect.common.security.util.SecurityUtils;
import com.detect.event.dto.AdminUser;
import com.detect.event.dto.NotificationQueryDTO;
import com.detect.event.entity.AlertRule;
import com.detect.event.entity.EventRecords;
import com.detect.event.entity.SysNotification;
import com.detect.event.feign.AuthUserClient;
import com.detect.event.mapper.EventRecordsMapper;
import com.detect.event.mapper.SysNotificationMapper;
import com.detect.event.vo.NotificationVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * {@link NotificationService} 单测：扇出(6c-3)——按管理员逐条写 sys_notification + 置事件 status=已推送，
 * 无管理员/Feign 失败时不写不置(返回 false)，content 组装(设备名+原因)；用户侧(6e-1)——分页/未读数按本人 scope，
 * 已读(本人未读→写 read_time、已读幂等、非本人 404)、全部已读返条数、删除(本人成功、非本人/不存在 404)。
 *
 * <p>{@link SecurityUtils} 为静态方法，用户侧用例以 Mockito 5 {@code mockStatic} 桩 getUserId；Mock Feign 客户端与两个 Mapper 隔离 DB。
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private AuthUserClient authUserClient;
    @Mock
    private SysNotificationMapper sysNotificationMapper;
    @Mock
    private EventRecordsMapper eventRecordsMapper;

    @InjectMocks
    private NotificationService service;

    private EventRecords record(long id, String deviceNum, String deviceName) {
        EventRecords e = new EventRecords();
        e.setId(id);
        e.setDeviceNum(deviceNum);
        e.setDeviceName(deviceName);
        e.setEventType(300);
        e.setCrowdNum(15);
        return e;
    }

    private AlertRule rule(long id, String name, int priority) {
        AlertRule r = new AlertRule();
        r.setId(id);
        r.setRuleName(name);
        r.setRuleType("CROWD_THRESHOLD");
        r.setPriority(priority);
        r.setNotifyEnabled(1);
        return r;
    }

    private AdminUser admin(long id, String username) {
        AdminUser a = new AdminUser();
        a.setId(id);
        a.setUsername(username);
        a.setNickname("管理员" + id);
        return a;
    }

    @Test
    void notifyRuleHit_adminsPresent_insertsPerAdminAndMarksPushed() {
        when(authUserClient.listAdmins()).thenReturn(R.ok(List.of(admin(1L, "admin"), admin(2L, "op"))));

        boolean pushed = service.notifyRuleHit(record(5L, "dev01", "南河湫水闸"),
                rule(1L, "人群聚集预警", 2), MatchResult.hit("crowdNum 15 命中阈值 >10"));

        assertTrue(pushed);
        // 每个管理员一条通知
        ArgumentCaptor<SysNotification> cap = ArgumentCaptor.forClass(SysNotification.class);
        verify(sysNotificationMapper, times(2)).insert(cap.capture());
        SysNotification n = cap.getAllValues().get(0);
        assertEquals(1L, n.getUserId());
        assertEquals("人群聚集预警", n.getTitle());
        assertEquals("南河湫水闸(dev01)：crowdNum 15 命中阈值 >10", n.getContent());
        assertEquals("alert", n.getType());
        assertEquals(5L, n.getBizId());
        assertEquals(2, n.getPriority().intValue());
        assertEquals(0, n.getReadFlag().intValue());
        // 事件 status→已推送(1)
        ArgumentCaptor<EventRecords> upd = ArgumentCaptor.forClass(EventRecords.class);
        verify(eventRecordsMapper).updateById(upd.capture());
        assertEquals(5L, upd.getValue().getId());
        assertEquals(1, upd.getValue().getStatus().intValue());
    }

    @Test
    void notifyRuleHit_noAdmins_returnsFalse_noInsertNoStatus() {
        when(authUserClient.listAdmins()).thenReturn(R.ok(List.of()));

        boolean pushed = service.notifyRuleHit(record(5L, "dev01", "南河湫水闸"),
                rule(1L, "人群聚集预警", 2), MatchResult.hit("x"));

        assertFalse(pushed);
        verify(sysNotificationMapper, never()).insert(any());
        verify(eventRecordsMapper, never()).updateById(any());
    }

    @Test
    void notifyRuleHit_nullData_returnsFalse() {
        when(authUserClient.listAdmins()).thenReturn(R.ok());

        assertFalse(service.notifyRuleHit(record(5L, "dev01", null), rule(1L, "r", 1), MatchResult.hit("x")));

        verify(sysNotificationMapper, never()).insert(any());
        verify(eventRecordsMapper, never()).updateById(any());
    }

    @Test
    void notifyRuleHit_feignThrows_caughtReturnsFalse_noStatus() {
        when(authUserClient.listAdmins()).thenThrow(new RuntimeException("auth down"));

        boolean pushed = service.notifyRuleHit(record(5L, "dev01", "南河湫水闸"),
                rule(1L, "r", 2), MatchResult.hit("x"));

        assertFalse(pushed);
        verify(sysNotificationMapper, never()).insert(any());
        verify(eventRecordsMapper, never()).updateById(any());
    }

    @Test
    void notifyRuleHit_noDeviceName_contentUsesDeviceNumOnly() {
        when(authUserClient.listAdmins()).thenReturn(R.ok(List.of(admin(1L, "admin"))));

        service.notifyRuleHit(record(5L, "dev09", null), rule(1L, "规则A", 1), MatchResult.hit("命中原因"));

        ArgumentCaptor<SysNotification> cap = ArgumentCaptor.forClass(SysNotification.class);
        verify(sysNotificationMapper).insert(cap.capture());
        assertEquals("dev09：命中原因", cap.getValue().getContent());
    }

    // ---------------- 用户侧(§4.4) ----------------

    private SysNotification notification(long id, long userId, int readFlag) {
        SysNotification n = new SysNotification();
        n.setId(id);
        n.setUserId(userId);
        n.setTitle("人群聚集预警");
        n.setContent("dev01 聚集 15 人");
        n.setType("alert");
        n.setBizId(5L);
        n.setPriority(2);
        n.setReadFlag(readFlag);
        n.setCreateTime(LocalDateTime.of(2026, 9, 12, 10, 0, 0));
        return n;
    }

    @Test
    void page_scopesToUserAndMapsVo() {
        when(sysNotificationMapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<SysNotification> p = inv.getArgument(0);
            p.setRecords(List.of(notification(1L, 1L, 0)));
            p.setTotal(5L);
            return p;
        });
        PageResult<NotificationVO> result;
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::getUserId).thenReturn(1L);
            result = service.page(new NotificationQueryDTO());
        }
        assertEquals(5L, result.getTotal());
        NotificationVO vo = result.getRecords().get(0);
        assertEquals(1L, vo.id());
        assertEquals("人群聚集预警", vo.title());
        assertEquals("alert", vo.type());
        assertEquals(0, vo.readFlag().intValue());
        assertEquals("2026-09-12 10:00:00", vo.createTime());
    }

    @Test
    void unreadCount_scopesToUser() {
        when(sysNotificationMapper.selectCount(any())).thenReturn(5L);
        long count;
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::getUserId).thenReturn(1L);
            count = service.unreadCount();
        }
        assertEquals(5L, count);
    }

    @Test
    void markRead_ownedUnread_setsReadFlagAndTime() {
        when(sysNotificationMapper.selectById(1L)).thenReturn(notification(1L, 1L, 0));
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::getUserId).thenReturn(1L);
            service.markRead(1L);
        }
        ArgumentCaptor<SysNotification> cap = ArgumentCaptor.forClass(SysNotification.class);
        verify(sysNotificationMapper).updateById(cap.capture());
        assertEquals(1, cap.getValue().getReadFlag().intValue());
        assertNotNull(cap.getValue().getReadTime());
    }

    @Test
    void markRead_notOwned_throws404_noUpdate() {
        when(sysNotificationMapper.selectById(1L)).thenReturn(notification(1L, 2L, 0));   // 属于 user 2
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::getUserId).thenReturn(1L);
            BizException ex = assertThrows(BizException.class, () -> service.markRead(1L));
            assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
        }
        verify(sysNotificationMapper, never()).updateById(any());
    }

    @Test
    void markRead_alreadyRead_idempotent_noUpdate() {
        when(sysNotificationMapper.selectById(1L)).thenReturn(notification(1L, 1L, 1));   // 已读
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::getUserId).thenReturn(1L);
            service.markRead(1L);
        }
        verify(sysNotificationMapper, never()).updateById(any());
    }

    @Test
    void markAllRead_returnsAffectedCount() {
        when(sysNotificationMapper.update(any(), any())).thenReturn(3);
        long read;
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::getUserId).thenReturn(1L);
            read = service.markAllRead();
        }
        assertEquals(3L, read);
    }

    @Test
    void delete_owned_deletes() {
        when(sysNotificationMapper.delete(any())).thenReturn(1);
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::getUserId).thenReturn(1L);
            service.delete(1L);
        }
        verify(sysNotificationMapper).delete(any());
    }

    @Test
    void delete_notOwnedOrMissing_throws404() {
        when(sysNotificationMapper.delete(any())).thenReturn(0);   // 0 行受影响
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::getUserId).thenReturn(1L);
            BizException ex = assertThrows(BizException.class, () -> service.delete(1L));
            assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
        }
    }
}
