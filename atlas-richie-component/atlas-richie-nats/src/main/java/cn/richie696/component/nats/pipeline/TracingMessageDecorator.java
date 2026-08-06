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

import cn.richie696.component.nats.strategy.NatsTracingSupport;
import io.nats.client.impl.Headers;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;

/**
 * 链路追踪装饰器
 *
 * <p>从 NATS Headers 提取 W3C trace context，创建 CONSUMER/SERVER span，
 * 在 finally 块中确保 span 结束。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class TracingMessageDecorator {

    /** 用于创建、结束 span 与注入/提取 W3C 的追踪支持实现。 */
    private final NatsTracingSupport tracingSupport;
    /** 决定 {@link #decorate} 内部走 {@code startConsumerSpan} 还是 {@code startServerSpan}。 */
    private final SpanKind spanKind;

    /**
     * 装饰器使用的 span 类型，决定 {@code NatsTracingSupport} 调用哪一个工厂方法。
     * <ul>
     *   <li>{@link #CONSUMER} — 异步消费消息（JetStream subscribe / pull），parent 从 Headers 提取；</li>
     *   <li>{@link #SERVER} — RPC 服务端处理请求，parent 从 Headers 提取。</li>
     * </ul>
     */
    public enum SpanKind {
        /** 异步消费端 span，对应 {@link NatsTracingSupport#startConsumerSpan}。 */
        CONSUMER,
        /** RPC 服务端 span，对应 {@link NatsTracingSupport#startServerSpan}。 */
        SERVER
    }

    /**
     * @param tracingSupport 追踪支持实现
     * @param spanKind       本装饰器要创建的 span 类型
     */
    public TracingMessageDecorator(NatsTracingSupport tracingSupport, SpanKind spanKind) {
        this.tracingSupport = tracingSupport;
        this.spanKind = spanKind;
    }

    /**
     * 创建装饰器函数：抽取 W3C 后开启 span，把 span 作为当前上下文执行内层 Handler，
     * finally 中无条件 {@code finishSpan} 并清理 MDC。
     *
     * @param inner 内层 Handler
     * @return 包装后的 Handler
     */
    public NatsMessageHandler decorate(NatsMessageHandler inner) {
        return message -> {
            Headers headers = message.getHeaders();
            // NATS Core 订阅端（部分 broker 配置下）允许 headers 为 null，提前 null-safe 兜底，避免下游 NPE。
            if (headers == null) {
                headers = new Headers();
            }

            // 根据装饰器预设的角色选用对应的 span 工厂：CONSUMER/SERVER 都会从 headers 提取 parent。
            Span span = switch (spanKind) {
                case SERVER -> tracingSupport.startServerSpan(message.getSubject(), headers);
                case CONSUMER -> tracingSupport.startConsumerSpan(message.getSubject(), headers);
            };

            boolean success = false;
            String errorMsg = null;
            // 用 try-with-resources(Scope) 保证 inner.handle 即便抛异常也能复原 OTel Context，不污染调用线程。
            try (Scope ignored = io.opentelemetry.context.Context.current().with(span).makeCurrent()) {
                inner.handle(message);
                success = true;
            } catch (Exception e) {
                // 记住异常信息后再 throw；finally 会写入 StatusCode.ERROR，对外行为不变。
                errorMsg = e.getMessage();
                throw e;
            } finally {
                tracingSupport.finishSpan(span, success, errorMsg);
            }
        };
    }
}
