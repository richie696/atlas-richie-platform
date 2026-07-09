/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.nats.pipeline;

import com.richie.component.nats.pipeline.NatsMessageHandler;
import com.richie.component.nats.strategy.NatsTracingSupport;
import io.nats.client.Message;
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

    private final NatsTracingSupport tracingSupport;
    private final SpanKind spanKind;

    public enum SpanKind {
        CONSUMER, SERVER
    }

    public TracingMessageDecorator(NatsTracingSupport tracingSupport, SpanKind spanKind) {
        this.tracingSupport = tracingSupport;
        this.spanKind = spanKind;
    }

    /**
     * 创建装饰器函数
     *
     * @param inner 内层 Handler
     * @return 包装后的 Handler
     */
    public NatsMessageHandler decorate(NatsMessageHandler inner) {
        return message -> {
            Headers headers = message.getHeaders();
            if (headers == null) {
                headers = new Headers();
            }

            Span span = switch (spanKind) {
                case SERVER -> tracingSupport.startServerSpan(message.getSubject(), headers);
                case CONSUMER -> tracingSupport.startConsumerSpan(message.getSubject(), headers);
            };

            boolean success = false;
            String errorMsg = null;
            try (Scope ignored = io.opentelemetry.context.Context.current().with(span).makeCurrent()) {
                inner.handle(message);
                success = true;
            } catch (Exception e) {
                errorMsg = e.getMessage();
                throw e;
            } finally {
                tracingSupport.finishSpan(span, success, errorMsg);
            }
        };
    }
}
