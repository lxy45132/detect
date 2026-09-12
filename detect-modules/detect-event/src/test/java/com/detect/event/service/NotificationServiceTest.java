package com.detect.event.service;

import com.detect.common.core.domain.R;
import com.detect.event.dto.AdminUser;
import com.detect.event.entity.AlertRule;
import com.detect.event.entity.EventRecords;
import com.detect.event.entity.SysNotification;
import com.detect.event.feign.AuthUserClient;
import com.detect.event.mapper.EventRecordsMapper;
import com.detect.event.mapper.SysNotificationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link NotificationService} 单测(6c-3)：按管理员逐条写 sys_notification + 置事件 status=已推送；
 * 无管理员/Feign 失败时不写不置(返回 false)；content 组装(设备名+原因)。Mock Feign 客户端与两个 Mapper。
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
}
