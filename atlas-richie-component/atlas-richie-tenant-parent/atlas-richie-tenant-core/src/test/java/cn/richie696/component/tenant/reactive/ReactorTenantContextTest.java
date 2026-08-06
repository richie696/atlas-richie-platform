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
package cn.richie696.component.tenant.reactive;

import cn.richie696.component.tenant.context.TenantContext;
import cn.richie696.component.tenant.context.ThreadLocalHolder;
import cn.richie696.contract.model.TenantPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ReactorTenantContext 单元测试 — 覆盖全部 5 个静态门面方法。
 *
 * <p>由于 {@code reactor-test} 不在测试 classpath，本测试直接使用
 * {@link Mono#block()} / {@link Mono#deferContextual} 来验证 Reactor Context 行为，
 * 而不是 {@code StepVerifier}。</p>
 */
@DisplayName("ReactorTenantContext — Reactive 静态门面")
class ReactorTenantContextTest {

    private TenantPrincipal principal;

    @BeforeEach
    void setUp() {
        TenantContext.init(new ThreadLocalHolder());
        principal = new TenantPrincipal().setTenantId(1001L).setTenantName("acme");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Mono<Void> consumedBy(TenantPrincipal p) {
        return Mono.deferContextual(ctx -> {
            TenantPrincipal got = ctx.getOrDefault(TenantContextKeys.TENANT_KEY, null);
            assertThat(got).isSameAs(p);
            return Mono.empty();
        });
    }

    @Nested
    @DisplayName("get()")
    class GetMethod {

        @Test
        @DisplayName("空 Context → Mono.empty()")
        void emptyContextYieldsEmptyMono() {
            AtomicReference<TenantPrincipal> captured = new AtomicReference<>();
            ReactorTenantContext.get()
                    .contextWrite(Context.empty())
                    .doOnNext(captured::set)
                    .switchIfEmpty(Mono.fromRunnable(() -> captured.set(null)))
                    .block();

            assertThat(captured.get()).isNull();
        }

        @Test
        @DisplayName("绑定 principal → Mono.just(principal)")
        void boundContextYieldsJustMono() {
            TenantPrincipal got = ReactorTenantContext.get()
                    .contextWrite(ReactorTenantContext.write(principal))
                    .block();

            assertThat(got).isSameAs(principal);
        }
    }

    @Nested
    @DisplayName("getTenantId()")
    class GetTenantIdMethod {

        @Test
        @DisplayName("空 Context → Mono.empty()")
        void emptyContextYieldsEmptyMono() {
            Long got = ReactorTenantContext.getTenantId()
                    .contextWrite(Context.empty())
                    .switchIfEmpty(Mono.just(0L))
                    .block();

            // switchIfEmpty 用 0 占位：若 getTenantId() 真的返回 empty，最终值 0L
            assertThat(got).isEqualTo(0L);
        }

        @Test
        @DisplayName("绑定 principal → Mono.just(tenantId)")
        void boundContextYieldsJustMono() {
            Long got = ReactorTenantContext.getTenantId()
                    .contextWrite(ReactorTenantContext.write(principal))
                    .block();

            assertThat(got).isEqualTo(1001L);
        }
    }

    @Nested
    @DisplayName("write(principal) / clear()")
    class WriteAndClear {

        @Test
        @DisplayName("write() 将 principal 写入 Context")
        void writeExposesPrincipal() {
            // 写入并验证下游能拿到
            ReactorTenantContext.<Void>get()
                    .flatMap(p -> consumedBy(p))
                    .contextWrite(ReactorTenantContext.write(principal))
                    .block();
        }

        @Test
        @DisplayName("clear() 不抛异常,返回的 Context 不含 TENANT_KEY")
        void clearRemovesPrincipal() {
            // 验证 Context.delete(K) 修复后不再抛 NPE,且 TENANT_KEY 真正被移除
            Context cleared = ReactorTenantContext.clear();
            assertThat(cleared).isNotNull();
            assertThat(cleared.hasKey(TenantContextKeys.TENANT_KEY)).isFalse();
        }

        @Test
        @DisplayName("write() 返回 reactor.util.context.Context 实例（write→read round-trip）")
        void writeReturnsContextThatRoundTrips() {
            Context ctx = ReactorTenantContext.write(principal);
            TenantPrincipal got = ReactorTenantContext.<TenantPrincipal>get()
                    .contextWrite(ctx)
                    .block();
            assertThat(got).isSameAs(principal);
        }
    }

    @Nested
    @DisplayName("bridgeToBlocking(Callable)")
    class BridgeCallable {

        @Test
        @DisplayName("空 Context → 直接执行 callable，不进入 runWithTenant")
        void emptyContextExecutesDirectly() {
            AtomicLong observedTenantId = new AtomicLong(-1L);
            Long result = ReactorTenantContext.<Long>bridgeToBlocking(() -> {
                observedTenantId.set(tenantIdSafely());
                return 42L;
            })
                    .contextWrite(Context.empty())
                    .block();

            assertThat(result).isEqualTo(42L);
            // 线程上没有 tenant 上下文 → getTenantId() 返回 null → -1L
            assertThat(observedTenantId.get()).isEqualTo(-1L);
        }

        @Test
        @DisplayName("绑定 principal → TenantContext.runWithTenant 包裹 callable")
        void boundContextWrapsInRunWithTenant() {
            AtomicLong observedTenantId = new AtomicLong(-1L);
            String result = ReactorTenantContext.bridgeToBlocking(() -> {
                observedTenantId.set(tenantIdSafely());
                return "ok";
            })
                    .contextWrite(ReactorTenantContext.write(principal))
                    .block();

            assertThat(result).isEqualTo("ok");
            assertThat(observedTenantId.get()).isEqualTo(1001L);
        }

        @Test
        @DisplayName("callable 抛 checked Exception → 包装为 RuntimeException")
        void checkedExceptionWrapped() {
            Mono<Void> mono = ReactorTenantContext.<Void>bridgeToBlocking(() -> {
                throw new Exception("boom");
            }).contextWrite(ReactorTenantContext.write(principal));

            Throwable thrown = null;
            try {
                mono.block();
            } catch (Throwable t) {
                thrown = t;
            }
            assertThat(thrown).isNotNull();
            // RuntimeException(boom) 由 ReactorTenantContext.bridgeToBlocking 显式 throw
            assertThat(thrown).isInstanceOf(RuntimeException.class);
            assertThat(thrown).hasMessageContaining("boom");
        }
    }

    @Nested
    @DisplayName("bridgeToBlocking(Runnable)")
    class BridgeRunnable {

        @Test
        @DisplayName("空 Context → 直接执行 runnable")
        void emptyContextExecutesDirectly() {
            AtomicLong observedTenantId = new AtomicLong(-1L);
            ReactorTenantContext.bridgeToBlocking(() ->
                            observedTenantId.set(tenantIdSafely()))
                    .contextWrite(Context.empty())
                    .block();

            assertThat(observedTenantId.get()).isEqualTo(-1L);
        }

        @Test
        @DisplayName("绑定 principal → TenantContext.runWithTenant 包裹 runnable")
        void boundContextWrapsInRunWithTenant() {
            AtomicLong observedTenantId = new AtomicLong(-1L);
            ReactorTenantContext.bridgeToBlocking(() ->
                            observedTenantId.set(tenantIdSafely()))
                    .contextWrite(ReactorTenantContext.write(principal))
                    .block();

            assertThat(observedTenantId.get()).isEqualTo(1001L);
        }
    }

    private static long tenantIdSafely() {
        Long tid = TenantContext.getTenantId();
        return tid == null ? -1L : tid;
    }
}
