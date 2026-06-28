package com.richie.component.nats.pipeline;

import com.richie.component.nats.strategy.NatsTracingSupport;
import com.richie.component.nats.strategy.OpenTelemetryNatsTracingSupport;
import io.nats.client.Message;
import io.nats.client.impl.Headers;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TracingMessageDecorator} 单元测试。
 *
 * <p>验证两种 SpanKind（CONSUMER / SERVER）下的装饰器行为：span 创建、success/failure 时
 * finishSpan 状态、finally 必执行。</p>
 *
 * <p>说明：装饰器内部调用 {@code Context.current().with(span).makeCurrent()}，
 * 需要真实的 {@link Span} 实现才能完成 Context 链式调用。Mockito mock 的 Span
 * 不实现 {@link io.opentelemetry.context.ImplicitContextKeyed}，会触发 NPE。
 * 因此用 {@link OpenTelemetry#noop()} 提供的 noop Tracer 创建真实 Span，并通过
 * {@code spy} 观察方法调用次数。</p>
 */
class TracingMessageDecoratorTest {

    @Test
    void decorate_consumerKind_shouldStartConsumerSpanAndFinishOnSuccess() throws Exception {
        NatsTracingSupport tracing = spyTracingSupport();
        TracingMessageDecorator decorator = new TracingMessageDecorator(tracing, TracingMessageDecorator.SpanKind.CONSUMER);
        AtomicBoolean innerCalled = new AtomicBoolean(false);

        NatsMessageHandler handler = decorator.decorate(msg -> innerCalled.set(true));
        handler.handle(mockMessage("test.subject", "msg-001"));

        assertThat(innerCalled.get()).isTrue();
        verify(tracing).startConsumerSpan(eq("test.subject"), any(Headers.class));
        verify(tracing).finishSpan(any(Span.class), eq(true), eq(null));
    }

    @Test
    void decorate_serverKind_shouldStartServerSpan() throws Exception {
        NatsTracingSupport tracing = spyTracingSupport();
        TracingMessageDecorator decorator = new TracingMessageDecorator(tracing, TracingMessageDecorator.SpanKind.SERVER);
        AtomicBoolean innerCalled = new AtomicBoolean(false);

        NatsMessageHandler handler = decorator.decorate(msg -> innerCalled.set(true));
        handler.handle(mockMessage("rpc.subject", "msg-002"));

        assertThat(innerCalled.get()).isTrue();
        verify(tracing).startServerSpan(eq("rpc.subject"), any(Headers.class));
        verify(tracing).finishSpan(any(Span.class), eq(true), eq(null));
    }

    @Test
    void decorate_innerThrows_shouldFinishSpanWithErrorMessage() throws Exception {
        NatsTracingSupport tracing = spyTracingSupport();
        TracingMessageDecorator decorator = new TracingMessageDecorator(tracing, TracingMessageDecorator.SpanKind.CONSUMER);

        RuntimeException boom = new RuntimeException("inner processing failed");
        NatsMessageHandler handler = decorator.decorate(msg -> {
            throw boom;
        });

        assertThatThrownBy(() -> handler.handle(mockMessage("subj", "id")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("inner processing failed");

        verify(tracing).startConsumerSpan(anyString(), any(Headers.class));
        verify(tracing).finishSpan(any(Span.class), eq(false), eq("inner processing failed"));
    }

    @Test
    void decorate_withNullHeaders_shouldStillInvokeStartSpan() throws Exception {
        NatsTracingSupport tracing = spyTracingSupport();
        AtomicReference<Headers> capturedHeaders = new AtomicReference<>();
        org.mockito.Mockito.doAnswer(inv -> {
            capturedHeaders.set(inv.getArgument(1));
            return inv.callRealMethod();
        }).when(tracing).startConsumerSpan(anyString(), any(Headers.class));

        TracingMessageDecorator decorator = new TracingMessageDecorator(tracing, TracingMessageDecorator.SpanKind.CONSUMER);

        Message msg = mock(Message.class);
        when(msg.getHeaders()).thenReturn(null);
        when(msg.getSubject()).thenReturn("subj");
        when(msg.getData()).thenReturn("data".getBytes());

        NatsMessageHandler handler = decorator.decorate(m -> { /* no-op */ });
        handler.handle(msg);

        // 装饰器将 null headers 替换为新的空 Headers 后传入 startConsumerSpan
        assertThat(capturedHeaders.get()).isNotNull();
        verify(tracing).startConsumerSpan(eq("subj"), any(Headers.class));
        verify(tracing).finishSpan(any(Span.class), eq(true), eq(null));
    }

    @Test
    void decorate_whenFinishSpanThrows_shouldPropagateAfterInner() throws Exception {
        NatsTracingSupport tracing = spyTracingSupport();
        doThrow(new RuntimeException("finish failed"))
                .when(tracing).finishSpan(any(Span.class), anyBoolean(), any());

        TracingMessageDecorator decorator = new TracingMessageDecorator(tracing, TracingMessageDecorator.SpanKind.CONSUMER);

        AtomicBoolean innerCalled = new AtomicBoolean(false);
        NatsMessageHandler handler = decorator.decorate(m -> innerCalled.set(true));

        assertThatThrownBy(() -> handler.handle(mockMessage("subj", "id")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("finish failed");
        assertThat(innerCalled.get()).isTrue();
    }

    private NatsTracingSupport spyTracingSupport() {
        return spy(new OpenTelemetryNatsTracingSupport(true, OpenTelemetry.noop()));
    }

    private Message mockMessage(String subject, String messageId) {
        Message msg = mock(Message.class);
        Headers headers = new Headers();
        headers.put("nats-message-id", messageId);
        when(msg.getHeaders()).thenReturn(headers);
        when(msg.getSubject()).thenReturn(subject);
        when(msg.getData()).thenReturn("data".getBytes());
        return msg;
    }
}
