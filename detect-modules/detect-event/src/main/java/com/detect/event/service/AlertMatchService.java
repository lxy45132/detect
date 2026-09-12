package com.detect.event.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.detect.event.entity.AlertHandleRecord;
import com.detect.event.entity.AlertRule;
import com.detect.event.entity.EventRecords;
import com.detect.event.enums.HandleStatusEnum;
import com.detect.event.mapper.AlertHandleRecordMapper;
import com.detect.event.mapper.AlertRuleMapper;
import com.detect.event.mapper.EventRecordsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 规则命中落库(§5.3 自动流转)。由 {@link com.detect.event.queue.RuleMatchConsumer} 异步调用：
 * 加载事件 → 匹配全部<b>启用</b>规则 → 取最高优先级命中(平手取小 id) → 升级 priority + 写 hit_rule_id +
 * 生成一条「系统」处理记录(审计留痕)。
 *
 * <p>纯落库，<b>不扇出通知</b>——notify_enabled=1 的站内通知与 event.status 置「已推送」由 6c-3 处理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertMatchService {

    /** 系统处理记录的处理人名称(§5.3 handlerName="系统")。 */
    private static final String SYSTEM_HANDLER = "系统";

    private final EventRecordsMapper eventRecordsMapper;
    private final AlertRuleMapper alertRuleMapper;
    private final AlertHandleRecordMapper alertHandleRecordMapper;
    private final RuleMatcher ruleMatcher;

    /**
     * 匹配单个事件并落库命中。
     *
     * @param eventId 事件 id(来自队列)
     * @return true=有规则命中并已落库；false=事件缺失/无启用规则/无命中
     */
    public boolean matchEvent(Long eventId) {
        if (eventId == null) {
            return false;
        }
        // selectById 受 @TableLogic 约束：已逻辑删/不存在均返回 null，直接跳过
        EventRecords record = eventRecordsMapper.selectById(eventId);
        if (record == null) {
            log.warn("[match] 事件不存在或已删除，跳过匹配 eventId={}", eventId);
            return false;
        }

        // 仅加载启用规则；按 id ASC 保证多命中平手时「取小 id」的确定性
        List<AlertRule> rules = alertRuleMapper.selectList(new LambdaQueryWrapper<AlertRule>()
                .eq(AlertRule::getEnabled, 1)
                .orderByAsc(AlertRule::getId));
        if (rules.isEmpty()) {
            log.debug("[match] 无启用规则，跳过 eventId={}", eventId);
            return false;
        }

        RuleMatchInput input = RuleMatchInput.from(record);
        AlertRule best = null;
        MatchResult bestResult = null;
        for (AlertRule rule : rules) {
            MatchResult r = ruleMatcher.match(rule, input);
            // 取最高优先级；平手取小 id(rules 已 id ASC，严格 > 天然保留先到的小 id)
            if (r.matched() && (best == null || rule.getPriority() > best.getPriority())) {
                best = rule;
                bestResult = r;
            }
        }
        if (best == null) {
            log.debug("[match] 事件 {} 未命中任何启用规则", eventId);
            return false;
        }

        persistHit(record, best, bestResult);
        return true;
    }

    /** 升级 priority + 写 hit_rule_id，并生成系统处理记录(§5.3)。 */
    private void persistHit(EventRecords record, AlertRule rule, MatchResult result) {
        // 1. 命中落库：hit_rule_id + priority(alert_rule.priority = 命中后优先级)
        EventRecords upd = new EventRecords();
        upd.setId(record.getId());
        upd.setHitRuleId(rule.getId());
        upd.setPriority(rule.getPriority());
        eventRecordsMapper.updateById(upd);

        // 2. 系统处理记录(审计留痕)：fromStatus=null(无前置人工流转)，toStatus=未处理(§5.3 toStatus=0)
        AlertHandleRecord hr = new AlertHandleRecord();
        hr.setEventId(record.getId());
        hr.setFromStatus(null);
        hr.setToStatus(HandleStatusEnum.PENDING.getCode());
        hr.setHandlerName(SYSTEM_HANDLER);
        hr.setHandleRemark("规则命中：" + rule.getRuleName() + "（" + result.reason() + "）");
        hr.setHandleTime(LocalDateTime.now());
        alertHandleRecordMapper.insert(hr);

        log.info("[match] 事件 {} 命中规则 {}({}) priority→{} reason={}",
                record.getId(), rule.getId(), rule.getRuleName(), rule.getPriority(), result.reason());

        // 3. notify_enabled=1 → 扇出站内通知并置 status=已推送(6c-3)
        if (Integer.valueOf(1).equals(rule.getNotifyEnabled())) {
            // TODO(6c-3)：Feign 调 auth @Inner 列 ADMIN → 批量写 sys_notification → event_records.status=1
            log.debug("[match] 规则 {} notify_enabled=1，事件 {} 待 6c-3 扇出通知", rule.getId(), record.getId());
        }
    }
}
