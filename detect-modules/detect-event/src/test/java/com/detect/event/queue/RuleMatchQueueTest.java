package com.detect.event.queue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RuleMatchQueue} 单测(6c-2)：LPUSH 投递(key + String 值)、BRPOP 解析、超时/非法值返回 null。
 * Mock {@link StringRedisTemplate} 与其 {@link ListOperations}，不依赖真实 Redis。
 */
@ExtendWith(MockitoExtension.class)
class RuleMatchQueueTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ListOperations<String, String> listOperations;

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    @Test
    void push_leftPushesKeyAndStringValue() {
        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        RuleMatchQueue queue = new RuleMatchQueue(stringRedisTemplate);

        queue.push(42L);

        verify(listOperations).leftPush(RuleMatchQueue.QUEUE_KEY, "42");
    }

    @Test
    void push_null_noop() {
        RuleMatchQueue queue = new RuleMatchQueue(stringRedisTemplate);

        queue.push(null);

        verify(stringRedisTemplate, never()).opsForList();
    }

    @Test
    void popBlocking_parsesLongFromRightPop() {
        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.rightPop(RuleMatchQueue.QUEUE_KEY, TIMEOUT)).thenReturn("7");
        RuleMatchQueue queue = new RuleMatchQueue(stringRedisTemplate);

        assertEquals(7L, queue.popBlocking(TIMEOUT));
    }

    @Test
    void popBlocking_timeoutReturnsNull() {
        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.rightPop(RuleMatchQueue.QUEUE_KEY, TIMEOUT)).thenReturn(null);
        RuleMatchQueue queue = new RuleMatchQueue(stringRedisTemplate);

        assertNull(queue.popBlocking(TIMEOUT));
    }

    @Test
    void popBlocking_invalidValueReturnsNull() {
        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.rightPop(RuleMatchQueue.QUEUE_KEY, TIMEOUT)).thenReturn("not-a-number");
        RuleMatchQueue queue = new RuleMatchQueue(stringRedisTemplate);

        assertNull(queue.popBlocking(TIMEOUT));
    }
}
