package com.richie.component.nats.pipeline;

import com.richie.component.nats.pipeline.NatsMessageHandler;
import com.richie.component.nats.strategy.NatsHeaderExtractor;
import io.nats.client.Message;
import lombok.extern.slf4j.Slf4j;

/**
 * 上下文恢复装饰器
 *
 * <p>从 NATS Headers 提取白名单头信息，恢复到 {@code HeaderContextHolder}，
 * 在 finally 块中清理上下文防止线程池泄漏。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class ContextRestorationDecorator {

    private final NatsHeaderExtractor headerExtractor;

    public ContextRestorationDecorator(NatsHeaderExtractor headerExtractor) {
        this.headerExtractor = headerExtractor;
    }

    /**
     * 创建装饰器函数
     *
     * @param inner 内层 Handler
     * @return 包装后的 Handler
     */
    public NatsMessageHandler decorate(NatsMessageHandler inner) {
        return message -> {
            try {
                headerExtractor.extract(message.getHeaders());
                inner.handle(message);
            } finally {
                // 清理 HeaderContextHolder，防止线程池复用时上下文泄漏
                com.richie.context.common.api.HeaderContextHolder.removeContext();
            }
        };
    }
}
