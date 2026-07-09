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
package com.richie.component.nats.pipeline;

import com.richie.component.nats.pipeline.ContextRestorationDecorator;
import com.richie.component.nats.pipeline.IdempotentMessageDecorator;
import com.richie.component.nats.pipeline.TracingMessageDecorator;
import com.richie.component.nats.strategy.NatsHeaderExtractor;
import com.richie.component.nats.strategy.NatsIdempotentChecker;
import com.richie.component.nats.strategy.NatsTracingSupport;

/**
 * NATS 订阅者工厂
 *
 * <p>根据场景（异步消费 / RPC 服务端）构建不同的消息处理管道。
 * 自动组装装饰器链：Tracing → Context → Idempotent → Business。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
public class NatsSubscriberFactory {

    private final NatsTracingSupport tracingSupport;
    private final NatsHeaderExtractor headerExtractor;
    private final NatsIdempotentChecker idempotentChecker;
    private final boolean idempotentEnabled;
    private final long idempotentTtlMillis;

    public NatsSubscriberFactory(NatsTracingSupport tracingSupport,
                                  NatsHeaderExtractor headerExtractor,
                                  NatsIdempotentChecker idempotentChecker,
                                  boolean idempotentEnabled,
                                  long idempotentTtlMillis) {
        this.tracingSupport = tracingSupport;
        this.headerExtractor = headerExtractor;
        this.idempotentChecker = idempotentChecker;
        this.idempotentEnabled = idempotentEnabled;
        this.idempotentTtlMillis = idempotentTtlMillis;
    }

    /**
     * 构建异步消费管道: Tracing(CONSUMER) → Context → Idempotent → Business
     *
     * @param businessHandler 业务处理 Handler
     * @return 完整管道 Handler
     */
    public NatsMessageHandler buildAsyncPipeline(NatsMessageHandler businessHandler) {
        var tracingDecorator = new TracingMessageDecorator(tracingSupport,
                TracingMessageDecorator.SpanKind.CONSUMER);
        var contextDecorator = new ContextRestorationDecorator(headerExtractor);

        var pipeline = new NatsMessageHandlerPipeline()
                .addDecorator(tracingDecorator::decorate)
                .addDecorator(contextDecorator::decorate);

        if (idempotentEnabled && idempotentChecker != null) {
            var idempotentDecorator = new IdempotentMessageDecorator(idempotentChecker, idempotentTtlMillis);
            pipeline.addDecorator(idempotentDecorator::decorate);
        }

        return pipeline.build(businessHandler);
    }

    /**
     * 构建 RPC 服务端管道: Tracing(SERVER) → Context → Business（无去重）
     *
     * @param businessHandler 业务处理 Handler
     * @return 完整管道 Handler
     */
    public NatsMessageHandler buildRpcPipeline(NatsMessageHandler businessHandler) {
        var tracingDecorator = new TracingMessageDecorator(tracingSupport,
                TracingMessageDecorator.SpanKind.SERVER);
        var contextDecorator = new ContextRestorationDecorator(headerExtractor);

        return new NatsMessageHandlerPipeline()
                .addDecorator(tracingDecorator::decorate)
                .addDecorator(contextDecorator::decorate)
                .build(businessHandler);
    }
}
