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
package com.richie.component.web.core.spi.support;

import com.richie.component.web.core.spi.WebInterceptor;
import com.richie.component.web.core.spi.WebRequestContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link DefaultWebInterceptorChain} 行为契约测试：执行顺序 / 短路 / 异常传播 / 链末 no-op。
 *
 * <p>对应 README.md §3.4 拦截器链顺序 + 短路语义，是 web-core SPI 入码（§5.2 A-1）的关键验收项。
 */
class DefaultWebInterceptorChainTest {

    @Test
    void proceed_runsInterceptorsInRegistrationOrder() throws Exception {
        List<String> trace = new ArrayList<>();
        List<WebInterceptor> interceptors = List.of(
                (ctx, chain) -> { trace.add("A:before"); chain.proceed(ctx); trace.add("A:after"); },
                (ctx, chain) -> { trace.add("B:before"); chain.proceed(ctx); trace.add("B:after"); },
                (ctx, chain) -> { trace.add("C:before"); /* 链末不 proceed */ trace.add("C:after"); }
        );
        DefaultWebInterceptorChain chain = new DefaultWebInterceptorChain(interceptors);

        chain.proceed(newCtx());

        // 经典洋葱模型：每层 before → 下一层 → after
        assertThat(trace).containsExactly(
                "A:before", "B:before", "C:before", "C:after", "B:after", "A:after"
        );
    }

    @Test
    void proceed_afterShortCircuit_isNoOp() throws Exception {
        // §3.4：被短路后即使后续 proceed 调用也不触发下游
        List<String> trace = new ArrayList<>();
        WebRequestContext ctx = newCtx();
        List<WebInterceptor> interceptors = List.of(
                (c, chain) -> {
                    trace.add("A:before");
                    c.markShortCircuit(429, "denied");
                    // 不调用 chain.proceed
                    trace.add("A:after");
                },
                (c, chain) -> { trace.add("B:before"); chain.proceed(c); trace.add("B:after"); }
        );
        DefaultWebInterceptorChain chain = new DefaultWebInterceptorChain(interceptors);

        chain.proceed(ctx);

        assertThat(trace).containsExactly("A:before", "A:after");
        assertThat(ctx.isShortCircuited()).isTrue();
        assertThat(ctx.responseStatus()).isEqualTo(429);

        // 验证：人为再调一次 proceed（在短路的 ctx 上）也不应触发 B
        chain.proceed(ctx);
        assertThat(trace).containsExactly("A:before", "A:after"); // 仍未触发 B
    }

    @Test
    void proceed_atChainEnd_isNoOp() throws Exception {
        // 链末不抛异常；让拦截器在末尾调一次 proceed 不破坏调用栈
        List<String> trace = new ArrayList<>();
        WebRequestContext ctx = newCtx();
        List<WebInterceptor> interceptors = List.of(
                (c, chain) -> { trace.add("only"); chain.proceed(c); /* 链末 no-op，不抛 */ }
        );
        DefaultWebInterceptorChain chain = new DefaultWebInterceptorChain(interceptors);

        chain.proceed(ctx);

        assertThat(trace).containsExactly("only");
    }

    @Test
    void proceed_propagatesExceptionFromNextInterceptor() throws Exception {
        WebRequestContext ctx = newCtx();
        IllegalStateException boom = new IllegalStateException("downstream boom");
        List<WebInterceptor> interceptors = List.of(
                (c, chain) -> { chain.proceed(c); }, // 触发下游异常
                (c, chain) -> { throw boom; }
        );
        DefaultWebInterceptorChain chain = new DefaultWebInterceptorChain(interceptors);

        assertThatThrownBy(() -> chain.proceed(ctx))
                .isSameAs(boom);
    }

    @Test
    void proceed_propagatesExceptionFromChainEndHandler() throws Exception {
        // 链末"穿透到 Spring Dispatcher"的兜底异常也向上传
        WebRequestContext ctx = newCtx();
        IllegalArgumentException boom = new IllegalArgumentException("dispatcher boom");
        List<WebInterceptor> interceptors = List.of(
                (c, chain) -> { try { chain.proceed(c); } catch (Exception e) { /* 吞掉 —— 不应发生，但若发生 */ throw new RuntimeException("wrapper", e); } },
                (c, chain) -> { throw boom; }
        );
        DefaultWebInterceptorChain chain = new DefaultWebInterceptorChain(interceptors);

        assertThatThrownBy(() -> chain.proceed(ctx))
                .isInstanceOf(RuntimeException.class)
                .hasCauseReference(boom);
    }

    @Test
    void interceptors_returnsUnmodifiableList() {
        List<WebInterceptor> interceptors = List.of((ctx, chain) -> {});
        DefaultWebInterceptorChain chain = new DefaultWebInterceptorChain(interceptors);

        List<WebInterceptor> view = chain.interceptors();
        assertThat(view).hasSize(1);

        assertThatThrownBy(() -> view.add((ctx, ch) -> {}))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void currentIndex_advancesAsProceedCalled() throws Exception {
        DefaultWebInterceptorChain chain = new DefaultWebInterceptorChain(List.of(
                (ctx, c) -> { /* 0 */ c.proceed(ctx); },
                (ctx, c) -> { /* 1 */ c.proceed(ctx); },
                (ctx, c) -> { /* 2 —— 末尾 */ }
        ));

        assertThat(chain.currentIndex()).isZero();
        chain.proceed(newCtx());
        // 走到末尾时 index = 3
        assertThat(chain.currentIndex()).isEqualTo(3);
    }

    @Test
    void constructor_nullInterceptorsRejected() {
        assertThatThrownBy(() -> new DefaultWebInterceptorChain(null))
                .isInstanceOf(NullPointerException.class);
    }

    private static WebRequestContext newCtx() {
        return MutableWebRequestContext.builder().path("/test").build();
    }
}