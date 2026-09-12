package com.detect.event.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 事件规则匹配轻量队列(Redis List)。receive 入库后 LPUSH eventId，消费者 BRPOP 异步匹配，
 * 不阻塞 webhook(§4.1.1「入库 → 异步触发布控规则匹配」)。
 *
 * <p>FIFO：左进(LPUSH)右出(BRPOP)，先入库先匹配。<b>at-most-once</b> 语义——弹出即从队列移除，
 * 若消费者处理中途宕机则该 eventId 丢失(轻量取舍；如需 at-least-once 须 BRPOPLPUSH 到「处理中」列表 + ack 回删)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleMatchQueue {

    /** 队列 key(全局单一 List)。 */
    public static final String QUEUE_KEY = "detect:event:rule-match:queue";

    private final StringRedisTemplate stringRedisTemplate;

    /** 入库后投递 eventId 到队首(LPUSH)。null 忽略。 */
    public void push(Long eventId) {
        if (eventId == null) {
            return;
        }
        stringRedisTemplate.opsForList().leftPush(QUEUE_KEY, eventId.toString());
        log.debug("[queue] LPUSH eventId={} -> {}", eventId, QUEUE_KEY);
    }

    /**
     * 阻塞弹出队尾(BRPOP)，超时返回 null——让消费者循环得以周期性检查停止标志、优雅退出。
     *
     * @param timeout 阻塞超时
     * @return eventId；超时或值非法返回 null(非法值直接丢弃)
     */
    public Long popBlocking(Duration timeout) {
        String raw = stringRedisTemplate.opsForList().rightPop(QUEUE_KEY, timeout);
        if (raw == null) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("[queue] 非法队列值，丢弃: {}", raw);
            return null;
        }
    }
}
