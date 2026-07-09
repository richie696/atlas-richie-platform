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
package com.richie.component.tenant.context;

import com.richie.contract.model.TenantPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@DisplayName("ScopedValueHolder — JDK ScopedValue 首选实现")
class ScopedValueHolderTest {

    private ScopedValueHolder holder;

    @BeforeEach
    void setUp() {
        holder = new ScopedValueHolder();
    }

    private TenantPrincipal principal(Long tenantId) {
        return new TenantPrincipal().setTenantId(tenantId);
    }

    @Nested
    @DisplayName("基本读取")
    class BasicRead {

        @Test
        @DisplayName("未绑定时 get() 返回 null")
        void getReturnsNullWhenNotBound() {
            assertThat(holder.get()).isNull();
        }
    }

    @Nested
    @DisplayName("runWithTenant(Runnable)")
    class RunWithTenantRunnable {

        @Test
        @DisplayName("作用域内可见绑定值")
        void bindsAndVisibleInScope() {
            AtomicReference<Long> captured = new AtomicReference<>();
            holder.runWithTenant(principal(1001L), () -> captured.set(holder.get().getTenantId()));
            assertThat(captured.get()).isEqualTo(1001L);
        }

        @Test
        @DisplayName("作用域结束后自动失效")
        void autoCleanAfterScope() {
            holder.runWithTenant(principal(1001L), () -> {});
            assertThat(holder.get()).isNull();
        }

        @Test
        @DisplayName("嵌套调用：内层遮蔽外层，内层结束后恢复外层")
        void nestingRestoresOuterValue() {
            holder.runWithTenant(principal(100L), () -> {
                assertThat(holder.get().getTenantId()).isEqualTo(100L);
                holder.runWithTenant(principal(200L), () -> {
                    assertThat(holder.get().getTenantId()).isEqualTo(200L);
                });
                assertThat(holder.get().getTenantId()).isEqualTo(100L);
            });
            assertThat(holder.get()).isNull();
        }

        @Test
        @DisplayName("异常后作用域仍结束，get 返回 null")
        void exceptionStillCleansUp() {
            try {
                holder.runWithTenant(principal(888L), (Runnable) () -> {
                    throw new RuntimeException("boom");
                });
            } catch (RuntimeException ignored) {
            }
            assertThat(holder.get()).isNull();
        }
    }

    @Nested
    @DisplayName("runWithTenant(Supplier)")
    class RunWithTenantSupplier {

        @Test
        @DisplayName("返回 Supplier 结果")
        void returnsSupplierResult() {
            String result = holder.runWithTenant(principal(3001L),
                    () -> "tenant=" + holder.get().getTenantId());
            assertThat(result).isEqualTo("tenant=3001");
        }

        @Test
        @DisplayName("嵌套 Supplier 遮蔽与恢复")
        void nestingSupplierRestoresOuter() {
            holder.runWithTenant(principal(10L), () -> {
                String inner = holder.runWithTenant(principal(20L),
                        () -> String.valueOf(holder.get().getTenantId()));
                assertThat(inner).isEqualTo("20");
                assertThat(holder.get().getTenantId()).isEqualTo(10L);
                return "ok";
            });
        }
    }

    @Nested
    @DisplayName("clear()")
    class ClearTests {

        @Test
        @DisplayName("clear 为空实现（ScopedValue 自动失效）")
        void clearIsNoOp() {
            holder.runWithTenant(principal(1001L), () -> {
                // clear 不影响 ScopedValue（作用域内仍可见）
                holder.clear();
                assertThat(holder.get()).isNotNull();
                assertThat(holder.get().getTenantId()).isEqualTo(1001L);
            });
        }
    }

    @Nested
    @DisplayName("StructuredTaskScope 继承")
    class StructuredTaskScopeInheritance {

        @Test
        @DisplayName("fork() 在 ScopedValue 作用域内执行时，子任务自动继承绑定")
        void forkInheritsBoundScopedValue() throws Exception {
            assertThatNoException().isThrownBy(() -> holder.runWithTenant(principal(7777L), () -> {
                try (var scope = StructuredTaskScope.open(Joiner.<Long>awaitAllSuccessfulOrThrow())) {
                    var subtask = scope.fork(() -> holder.get().getTenantId());
                    scope.join();
                    assertThat(subtask.get()).isEqualTo(7777L);
                } catch (Exception e) {
                    throw new AssertionError("StructuredTaskScope failed", e);
                }
            }));
        }

        @Test
        @DisplayName("fork() 在 ScopedValue 作用域外执行时，子任务看不到绑定（返回 null）")
        void forkOutsideScopeDoesNotInherit() throws Exception {
            try (var scope = StructuredTaskScope.open(Joiner.<Long>awaitAllSuccessfulOrThrow())) {
                var subtask = scope.fork(() -> {
                    TenantPrincipal p = holder.get();
                    return p != null ? p.getTenantId() : null;
                });
                scope.join();
                assertThat(subtask.get()).isNull();
            }
        }
    }
}
