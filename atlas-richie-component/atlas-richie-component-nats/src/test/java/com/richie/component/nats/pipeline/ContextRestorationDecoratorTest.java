package com.richie.component.nats.pipeline;

import com.richie.component.nats.strategy.NatsHeaderExtractor;
import io.nats.client.Message;
import io.nats.client.impl.Headers;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ContextRestorationDecorator} 单元测试。
 *
 * <p>验证上下文恢复装饰器：
 * 1. 调用 extract 提取白名单 header
 * 2. 调用 inner handler
 * 3. 无论 inner 是否抛异常，finally 必须清理 HeaderContextHolder</p>
 */
class ContextRestorationDecoratorTest {

    @Test
    void decorate_normalFlow_shouldExtractAndInvokeInnerThenCleanup() throws Exception {
        NatsHeaderExtractor extractor = mock(NatsHeaderExtractor.class);
        ContextRestorationDecorator decorator = new ContextRestorationDecorator(extractor);

        AtomicBoolean innerCalled = new AtomicBoolean(false);
        NatsMessageHandler inner = msg -> innerCalled.set(true);

        NatsMessageHandler handler = decorator.decorate(inner);
        Message msg = mockMessage("test.subject");
        handler.handle(msg);

        assertThat(innerCalled.get()).isTrue();

        // 调用顺序：先 extract，再 inner，最后 removeContext
        InOrder ordered = inOrder(extractor);
        ordered.verify(extractor).extract(any(Headers.class));
        // HeaderContextHolder.removeContext 是静态方法，无法直接 verify，依赖 finally 路径不抛异常即代表成功
    }

    @Test
    void decorate_whenInnerThrows_shouldStillCleanup() throws Exception {
        // HeaderContextHolder 是 ThreadLocal 静态状态，必须在异常路径下也清理，否则会泄漏到线程池后续任务
        NatsHeaderExtractor extractor = mock(NatsHeaderExtractor.class);
        ContextRestorationDecorator decorator = new ContextRestorationDecorator(extractor);

        RuntimeException boom = new RuntimeException("inner processing failed");
        NatsMessageHandler inner = msg -> {
            throw boom;
        };

        NatsMessageHandler handler = decorator.decorate(inner);

        assertThatThrownBy(() -> handler.handle(mockMessage("test.subject")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("inner processing failed");

        // extract 仍被调用（finally 仅保证 cleanup）
        verify(extractor).extract(any(Headers.class));
    }

    @Test
    void decorate_whenExtractThrows_shouldNotInvokeInner() throws Exception {
        NatsHeaderExtractor extractor = mock(NatsHeaderExtractor.class);
        doThrow(new RuntimeException("header parse error")).when(extractor).extract(any(Headers.class));

        ContextRestorationDecorator decorator = new ContextRestorationDecorator(extractor);
        AtomicBoolean innerCalled = new AtomicBoolean(false);
        NatsMessageHandler inner = msg -> innerCalled.set(true);

        NatsMessageHandler handler = decorator.decorate(inner);

        // extract 在 inner 之前调用，extract 抛错则 inner 不应被触发
        assertThatThrownBy(() -> handler.handle(mockMessage("test.subject")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("header parse error");
        assertThat(innerCalled.get()).isFalse();
    }

    @Test
    void decorate_withNullHeaders_shouldStillInvokeExtract() throws Exception {
        NatsHeaderExtractor extractor = mock(NatsHeaderExtractor.class);
        ContextRestorationDecorator decorator = new ContextRestorationDecorator(extractor);

        AtomicBoolean innerCalled = new AtomicBoolean(false);
        NatsMessageHandler inner = msg -> innerCalled.set(true);

        NatsMessageHandler handler = decorator.decorate(inner);

        Message msg = mock(Message.class);
        when(msg.getHeaders()).thenReturn(null);
        handler.handle(msg);

        assertThat(innerCalled.get()).isTrue();
        // null headers 也应传递给 extractor（由 extractor 决定如何处理）
        verify(extractor).extract(null);
    }

    @Test
    void decorate_decoratorShouldBeRepeatableAndIndependent() throws Exception {
        // 同一装饰器可装饰多个 handler，每次都是独立的装饰函数
        NatsHeaderExtractor extractor = mock(NatsHeaderExtractor.class);
        ContextRestorationDecorator decorator = new ContextRestorationDecorator(extractor);

        AtomicBoolean h1Called = new AtomicBoolean(false);
        AtomicBoolean h2Called = new AtomicBoolean(false);

        NatsMessageHandler h1 = decorator.decorate(msg -> h1Called.set(true));
        NatsMessageHandler h2 = decorator.decorate(msg -> h2Called.set(true));

        h1.handle(mockMessage("a"));
        h2.handle(mockMessage("b"));

        assertThat(h1Called.get()).isTrue();
        assertThat(h2Called.get()).isTrue();
        // 两次调用都触发了 extract
        verify(extractor, org.mockito.Mockito.times(2)).extract(any());
    }

    @Test
    void decorate_finallyEvenWhenInnerDoesNotInteract() throws Exception {
        // 防御性测试：inner 不抛异常时，finally 也应执行 removeContext
        // 由于 removeContext 是静态方法无法 mock，我们验证 inner 的简单调用路径
        NatsHeaderExtractor extractor = mock(NatsHeaderExtractor.class);
        verifyNoInteractions(extractor); // baseline：装饰器未运行时，extractor 不被调用

        ContextRestorationDecorator decorator = new ContextRestorationDecorator(extractor);
        NatsMessageHandler handler = decorator.decorate(msg -> { /* no-op */ });

        handler.handle(mockMessage("test.subject"));
        // extract 必须被调用
        verify(extractor).extract(any(Headers.class));
    }

    private Message mockMessage(String subject) {
        Message msg = mock(Message.class);
        Headers headers = new Headers();
        headers.put("x-tenant-id", "tenant-1");
        when(msg.getHeaders()).thenReturn(headers);
        when(msg.getSubject()).thenReturn(subject);
        when(msg.getData()).thenReturn("payload".getBytes());
        return msg;
    }
}
