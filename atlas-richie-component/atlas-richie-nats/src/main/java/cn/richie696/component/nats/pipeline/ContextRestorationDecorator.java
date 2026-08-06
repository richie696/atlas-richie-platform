/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.richie696.component.nats.pipeline;

import cn.richie696.component.nats.strategy.NatsHeaderExtractor;
import cn.richie696.context.common.api.HeaderContextHolder;
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

    /** 将 NATS Headers 中的白名单字段恢复到 {@link HeaderContextHolder} 的策略实现。 */
    private final NatsHeaderExtractor headerExtractor;

    /**
     * @param headerExtractor Header 提取策略实现
     */
    public ContextRestorationDecorator(NatsHeaderExtractor headerExtractor) {
        this.headerExtractor = headerExtractor;
    }

    /**
     * 创建装饰器函数：先把 Headers 中的上下文恢复到当前线程，再执行内层 Handler，
     * finally 清理避免线程池复用时上游痕迹混入下游消息。
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
                // NATS 消费者可能复用了 IO 线程或业务线程池，必须显式 removeContext，
                // 否则下一个消息会继承上个消息的租户/用户上下文，造成上下文串扰。
                HeaderContextHolder.removeContext();
            }
        };
    }
}
