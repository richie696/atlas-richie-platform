package com.richie.component.redis.streammq;

import com.richie.component.redis.streammq.function.StreamFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Redis StreamMQ 全局静态门面，提供基于 Redis Stream 的可靠消息队列能力。
 * <p>替代 {@code GlobalCache.stream()} / {@code GlobalCache.messaging()} 等分散入口。</p>
 * <p><b>设计原则：</b></p>
 * <ul>
 *   <li><b>Stream</b>：基于 Redis Stream 的消息队列（持久化、消费组、ACK、回溯）</li>
 *   <li><b>Messaging</b>：基于 Pub/Sub 的发布订阅通知（轻量广播、在线推送）</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026/06/05
 */
@Component
public class StreamMQ {

    private static final AtomicReference<StreamMQDelegate> DELEGATE = new AtomicReference<>();

    private StreamMQ() {
    }

    // ========================================================================
    //  Stream 消息队列操作
    // ========================================================================

    /**
     * 获取 Stream 消息队列操作接口。
     * <p>提供基于 Redis Stream 的可靠消息发布与消费能力（持久化、消费组、ACK）。</p>
     *
     * @return StreamFunction 实例
     */
    public static StreamFunction stream() {
        return DELEGATE.get().streamFn;
    }

    // ========================================================================
    //  Spring 注入
    // ========================================================================

    @Autowired
    public void setDelegate(StreamMQDelegate delegate) {
        if (StreamMQ.DELEGATE.get() == null) {
            synchronized (StreamMQ.class) {
                if (StreamMQ.DELEGATE.get() == null) {
                    StreamMQ.DELEGATE.set(delegate);
                }
            }
        }
    }

    /**
     * StreamMQ 内部委托，持有所有 Stream/Messaging 操作的 bean 引用。
     */
    @Component
    @RequiredArgsConstructor
    static class StreamMQDelegate {

        /** Stream 消息队列操作接口 */
        private final StreamFunction streamFn;

    }
}
