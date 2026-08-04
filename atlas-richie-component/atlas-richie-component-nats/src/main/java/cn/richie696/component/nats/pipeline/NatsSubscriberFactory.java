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
import cn.richie696.component.nats.strategy.NatsIdempotentChecker;
import cn.richie696.component.nats.strategy.NatsTracingSupport;

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

    /** 链路追踪支持；为空时 TracingMessageDecorator 内部仍可工作（取决于其实现）。 */
    private final NatsTracingSupport tracingSupport;
    /** Header 上下文恢复器；为空时跳过 ContextRestorationDecorator。 */
    private final NatsHeaderExtractor headerExtractor;
    /** 幂等去重实现；为 {@code null} 或未启用时不挂载装饰器。 */
    private final NatsIdempotentChecker idempotentChecker;
    /** 是否启用幂等去重；与 {@link #idempotentChecker} 联合判断。 */
    private final boolean idempotentEnabled;
    /** 幂等记录的 TTL（毫秒），仅在启用幂等时生效。 */
    private final long idempotentTtlMillis;

    /**
     * @param tracingSupport       链路追踪支持
     * @param headerExtractor      Header 上下文恢复器
     * @param idempotentChecker    幂等去重实现
     * @param idempotentEnabled    是否启用幂等去重
     * @param idempotentTtlMillis  幂等记录 TTL（毫秒）
     */
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
     * 构建异步消费管道: Tracing(CONSUMER) → Context → Idempotent → Business。
     * 顺序保证：trace 必须在最外层以便覆盖整条上下文恢复+去重+业务链路；
     * Context 在 Idempotent 之前以便消息 ID 抽取能命中 Header 中的追踪信息。
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

        // 仅当同时启用且 checker 非空时才挂载幂等装饰器，避免 noop 包装带来的额外开销。
        if (idempotentEnabled && idempotentChecker != null) {
            var idempotentDecorator = new IdempotentMessageDecorator(idempotentChecker, idempotentTtlMillis);
            pipeline.addDecorator(idempotentDecorator::decorate);
        }

        return pipeline.build(businessHandler);
    }

    /**
     * 构建 RPC 服务端管道: Tracing(SERVER) → Context → Business（无去重）。
     * RPC 一般由调用方保证语义幂等，因此不挂载 {@link IdempotentMessageDecorator}。
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
