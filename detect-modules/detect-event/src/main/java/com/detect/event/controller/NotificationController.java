package com.detect.event.controller;

import com.detect.common.core.domain.PageResult;
import com.detect.common.core.domain.R;
import com.detect.event.dto.NotificationQueryDTO;
import com.detect.event.service.NotificationService;
import com.detect.event.vo.NotificationVO;
import com.detect.event.vo.ReadAllVO;
import com.detect.event.vo.UnreadCountVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内通知接口(接口文档 §4.4)，路径前缀 {@code /notifications}(网关 {@code /admin/event} 已被 StripPrefix)。
 *
 * <p>全部为 JWT 前端接口；{@code user_id} 从令牌上下文取，用户只能操作自己的通知(服务层强制，非本人报 404)。
 * 字面路径({@code /page}、{@code /unread-count}、{@code /read-all})与 {@code /{id}/read}(双段)、{@code /{id}}(DELETE)无路由冲突。
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** 我的通知分页(§4.4.1)：可按 readFlag/type 过滤，create_time DESC。 */
    @GetMapping("/page")
    public R<PageResult<NotificationVO>> page(NotificationQueryDTO query) {
        return R.ok(notificationService.page(query));
    }

    /** 未读数(§4.4.2)：前端红点。 */
    @GetMapping("/unread-count")
    public R<UnreadCountVO> unreadCount() {
        return R.ok(new UnreadCountVO(notificationService.unreadCount()));
    }

    /** 标记单条已读(§4.4.3)：写 read_time；非本人/不存在报 404。 */
    @PutMapping("/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return R.ok();
    }

    /** 全部标记已读(§4.4.4)：返回本次转已读条数 {@code {read:N}}。 */
    @PutMapping("/read-all")
    public R<ReadAllVO> markAllRead() {
        return R.ok(new ReadAllVO(notificationService.markAllRead()));
    }

    /** 删除通知(§4.4.5)：物理删，ownership-scoped；非本人/不存在报 404。 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        notificationService.delete(id);
        return R.ok();
    }
}
