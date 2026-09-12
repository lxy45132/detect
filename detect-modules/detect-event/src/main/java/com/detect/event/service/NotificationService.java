package com.detect.event.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.detect.common.core.constant.CommonConstants;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 站内通知域服务：<b>扇出</b>(§5.3，规则命中→写 sys_notification + 置事件已推送) + <b>用户侧</b>(§4.4，分页/未读数/已读/删除)。
 *
 * <p>扇出：规则命中且 notify_enabled=1 时，经 Feign 调 auth 列全部管理员，为每人写一条 sys_notification(type=alert)，
 * 并将 event_records.status 置「已推送」(1)。通知失败<b>不回滚</b>命中(hit_rule_id/priority/系统留痕已在
 * {@link AlertMatchService} 落库)——异常内部吞并记日志、返回 false；status 仅在至少写出一条通知后才置 1。
 *
 * <p>用户侧：{@code user_id} 一律从 JWT 上下文取({@link SecurityUtils})，用户只能操作自己的通知；
 * 非本人/不存在统一报 {@code 404}(不泄露存在性)。sys_notification 无 del_flag，删除为物理删(§4.4.5)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** 推送状态：1=已推送(0=未推送，见 §3.2 event_records.status)。 */
    private static final int STATUS_PUSHED = 1;
    private static final String TYPE_ALERT = "alert";
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern(CommonConstants.DATETIME_PATTERN);
    private static final long MAX_PAGE_SIZE = 200L;
    private static final int READ = 1;
    private static final int UNREAD = 0;

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

    // ---------------- 用户侧(§4.4) ----------------

    /** 我的通知分页(§4.4.1)：仅本人，按 {@code create_time DESC, id DESC}；可按 readFlag/type 过滤。 */
    public PageResult<NotificationVO> page(NotificationQueryDTO q) {
        Long userId = SecurityUtils.getUserId();
        long current = Math.max(q.getCurrent(), 1L);
        long size = Math.min(Math.max(q.getSize(), 1L), MAX_PAGE_SIZE);
        QueryWrapper<SysNotification> w = new QueryWrapper<>();
        w.eq("user_id", userId);
        w.eq(q.getReadFlag() != null, "read_flag", q.getReadFlag());
        w.eq(StringUtils.hasText(q.getType()), "type", q.getType());
        w.orderByDesc("create_time").orderByDesc("id");
        Page<SysNotification> page = sysNotificationMapper.selectPage(new Page<>(current, size), w);
        List<NotificationVO> vos = page.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(vos, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 未读数(§4.4.2)：本人 {@code read_flag=0} 计数(前端红点)。 */
    public long unreadCount() {
        Long userId = SecurityUtils.getUserId();
        return sysNotificationMapper.selectCount(
                new QueryWrapper<SysNotification>().eq("user_id", userId).eq("read_flag", UNREAD));
    }

    /** 标记单条已读(§4.4.3)：非本人/不存在报 {@code 404}；已读则幂等直接成功(不覆盖首次 read_time)。 */
    public void markRead(Long id) {
        Long userId = SecurityUtils.getUserId();
        SysNotification n = sysNotificationMapper.selectById(id);
        if (n == null || !userId.equals(n.getUserId())) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (n.getReadFlag() != null && n.getReadFlag() == READ) {
            return;
        }
        SysNotification upd = new SysNotification();
        upd.setId(id);
        upd.setReadFlag(READ);
        upd.setReadTime(LocalDateTime.now());
        sysNotificationMapper.updateById(upd);
    }

    /** 全部标记已读(§4.4.4)：本人 {@code read_flag=0} 批量置已读，返回受影响条数。 */
    public long markAllRead() {
        Long userId = SecurityUtils.getUserId();
        SysNotification upd = new SysNotification();
        upd.setReadFlag(READ);
        upd.setReadTime(LocalDateTime.now());
        return sysNotificationMapper.update(upd,
                new QueryWrapper<SysNotification>().eq("user_id", userId).eq("read_flag", UNREAD));
    }

    /** 删除通知(§4.4.5)：物理删，ownership-scoped；非本人/不存在报 {@code 404}。 */
    public void delete(Long id) {
        Long userId = SecurityUtils.getUserId();
        int rows = sysNotificationMapper.delete(
                new QueryWrapper<SysNotification>().eq("id", id).eq("user_id", userId));
        if (rows == 0) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
    }

    private NotificationVO toVO(SysNotification n) {
        return new NotificationVO(n.getId(), n.getTitle(), n.getContent(), n.getType(), n.getBizId(),
                n.getPriority(), n.getReadFlag(),
                n.getCreateTime() == null ? null : n.getCreateTime().format(DT));
    }
}
