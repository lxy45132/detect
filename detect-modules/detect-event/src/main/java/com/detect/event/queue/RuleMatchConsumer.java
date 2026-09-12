package com.detect.event.queue;

import com.detect.event.service.AlertMatchService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 规则匹配队列消费者：单守护线程 BRPOP 循环，取出 eventId 交 {@link AlertMatchService} 落库命中。
 *
 * <p>单线程足矣(事件量低、天然顺序处理)；单条异常被 catch 后继续消费下一条，不中断循环。
 * BRPOP 带 2s 超时，使优雅停机时线程能在 {@code running=false} 后及时退出(超时回到循环顶检查标志)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleMatchConsumer {

    /** BRPOP 阻塞超时：足够短以便快速响应停机，足够长以免空转打满 CPU。 */
    private static final Duration POP_TIMEOUT = Duration.ofSeconds(2);

    private final RuleMatchQueue ruleMatchQueue;
    private final AlertMatchService alertMatchService;

    private volatile boolean running = false;
    private Thread worker;

    @PostConstruct
    public void start() {
        running = true;
        worker = new Thread(this::loop, "rule-match-consumer");
        worker.setDaemon(true);
        worker.start();
        log.info("[consumer] 规则匹配消费者已启动(BRPOP {})", RuleMatchQueue.QUEUE_KEY);
    }

    private void loop() {
        while (running) {
            try {
                Long eventId = ruleMatchQueue.popBlocking(POP_TIMEOUT);
                if (eventId != null) {
                    alertMatchService.matchEvent(eventId);
                }
            } catch (Exception e) {
                // 停机中(running=false)导致的异常属预期，静默退出；否则记录后继续下一条
                if (!running) {
                    log.info("[consumer] 停止中，退出循环");
                    return;
                }
                log.error("[consumer] 处理队列项失败: {}", e.getMessage(), e);
            }
        }
        log.info("[consumer] 规则匹配消费者已停止");
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
        log.info("[consumer] 正在停止规则匹配消费者...");
    }
}
