package com.detect.event.service;

import com.detect.common.core.domain.R;
import com.detect.event.dto.AdminUser;
import com.detect.event.entity.AlertRule;
import com.detect.event.entity.EventRecords;
import com.detect.event.entity.SysNotification;
import com.detect.event.feign.AuthUserClient;
import com.detect.event.mapper.EventRecordsMapper;
import com.detect.event.mapper.SysNotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 预警通知扇出(§5.3 + §4.4)。规则命中且 notify_enabled=1 时，经 Feign 调 auth 列全部管理员，
 * 为每人写一条 sys_notification(type=alert)，并将 event_records.status 置「已推送」(1)。
 *
 * <p>通知失败<b>不回滚</b>命中(hit_rule_id/priority/系统留痕已在 {@link AlertMatchService} 落库)——
 * 异常内部吞并记日志、返回 false；status 仅在至少写出一条通知后才置 1。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** 推送状态：1=已推送(0=未推送，见 §3.2 event_records.status)。 */
    private static final int STATUS_PUSHED = 1;
    private static final String TYPE_ALERT = "alert";

    private final AuthUserClient authUserClient;
    private final SysNotificationMapper sysNotificationMapper;
    private final EventRecordsMapper eventRecordsMapper;

    /**
     * 扇出规则命中通知给所有管理员。
     *
     * @return true=已写入至少一条通知并置事件 status=1；false=无管理员或调用失败(未推送)
     */
    public boolean notifyRuleHit(EventRecords record, AlertRule rule, MatchResult result) {
        try {
            List<AdminUser> admins = fetchAdmins();
            if (admins.isEmpty()) {
                log.warn("[notify] 无管理员可通知，事件 {} 保持未推送", record.getId());
                return false;
            }
            String title = rule.getRuleName();
            String content = buildContent(record, result);
            for (AdminUser a : admins) {
                SysNotification n = new SysNotification();
                n.setUserId(a.getId());
                n.setTitle(title);
                n.setContent(content);
                n.setType(TYPE_ALERT);
                n.setBizId(record.getId());
                n.setPriority(rule.getPriority());
                n.setReadFlag(0);
                // create_time 由 MetaObjectHandler 自动填充(SysNotification.createTime @TableField(fill=INSERT))
                sysNotificationMapper.insert(n);
            }
            markPushed(record.getId());
            log.info("[notify] 事件 {} 命中规则 {} 扇出 {} 条通知，status→{}",
                    record.getId(), rule.getId(), admins.size(), STATUS_PUSHED);
            return true;
        } catch (Exception e) {
            log.error("[notify] 事件 {} 扇出通知失败: {}", record.getId(), e.getMessage(), e);
            return false;
        }
    }

    private List<AdminUser> fetchAdmins() {
        R<List<AdminUser>> resp = authUserClient.listAdmins();
        if (resp == null || resp.getData() == null) {
            return List.of();
        }
        return resp.getData();
    }

    /** 设备标识 + 命中原因，如「南河湫水闸(dev01)：crowdNum 15 命中阈值 >10」。 */
    private String buildContent(EventRecords record, MatchResult result) {
        String device = record.getDeviceName() != null && !record.getDeviceName().isBlank()
                ? record.getDeviceName() + "(" + record.getDeviceNum() + ")"
                : record.getDeviceNum();
        return device + "：" + result.reason();
    }

    private void markPushed(Long eventId) {
        EventRecords upd = new EventRecords();
        upd.setId(eventId);
        upd.setStatus(STATUS_PUSHED);
        eventRecordsMapper.updateById(upd);
    }
}
