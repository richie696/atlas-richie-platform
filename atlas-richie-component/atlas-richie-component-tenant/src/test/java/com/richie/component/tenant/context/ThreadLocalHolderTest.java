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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ThreadLocalHolder — plain ThreadLocal + micrometer context-propagation")
class ThreadLocalHolderTest {

    private ThreadLocalHolder holder;

    @BeforeEach
    void setUp() {
        holder = new ThreadLocalHolder();
    }

    @AfterEach
    void tearDown() {
        holder.clear();
    }

    private TenantPrincipal principal(Long tenantId) {
        return new TenantPrincipal().setTenantId(tenantId);
    }

    @Nested
    @DisplayName("基本读写")
    class BasicReadWrite {

        @Test
        @DisplayName("初始状态 get() 返回 null")
        void getReturnsNullWhenNotBound() {
            assertThat(holder.get()).isNull();
        }

        @Test
        @DisplayName("set 后 get 返回对应 principal")
        void setAndGet() {
            holder.set(principal(1001L));
            assertThat(holder.get()).isNotNull();
            assertThat(holder.get().getTenantId()).isEqualTo(1001L);
        }

        @Test
        @DisplayName("clear 后 get 返回 null")
        void clearRemovesValue() {
            holder.set(principal(1001L));
            holder.clear();
            assertThat(holder.get()).isNull();
        }
    }

    @Nested
    @DisplayName("runWithTenant(Runnable)")
    class RunWithTenantRunnable {

        @Test
        @DisplayName("绑定租户并在作用域内可见")
        void bindsAndVisibleInScope() {
            AtomicReference<Long> captured = new AtomicReference<>();
            holder.runWithTenant(principal(2001L), () -> captured.set(holder.get().getTenantId()));
            assertThat(captured.get()).isEqualTo(2001L);
        }

        @Test
        @DisplayName("作用域结束后自动清理（无先前值）")
        void autoCleanAfterScope() {
            holder.runWithTenant(principal(2001L), () -> {});
            assertThat(holder.get()).isNull();
        }

        @Test
        @DisplayName("嵌套调用：内层结束后恢复外层值")
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
        @DisplayName("任务抛异常后仍恢复先前值")
        void exceptionStillRestoresPreviousValue() {
            holder.set(principal(999L));
            try {
                holder.runWithTenant(principal(888L), (Runnable) () -> {
                    throw new RuntimeException("boom");
                });
            } catch (RuntimeException ignored) {
            }
            assertThat(holder.get().getTenantId()).isEqualTo(999L);
        }
    }

    @Nested
    @DisplayName("runWithTenant(Supplier)")
    class RunWithTenantSupplier {

        @Test
        @DisplayName("返回任务执行结果")
        void returnsSupplierResult() {
            String result = holder.runWithTenant(principal(3001L), () -> "tenant=" + holder.get().getTenantId());
            assertThat(result).isEqualTo("tenant=3001");
        }

        @Test
        @DisplayName("作用域结束后自动清理")
        void autoCleanAfterSupplierScope() {
            holder.runWithTenant(principal(3001L), () -> "done");
            assertThat(holder.get()).isNull();
        }

        @Test
        @DisplayName("嵌套 Supplier 恢复外层值")
        void nestingRestoresOuterValueSupplier() {
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
    @DisplayName("ThreadLocalAccessor")
    class AccessorTests {

        @Test
        @DisplayName("Accessor key 为 tenant-principal")
        void accessorKeyIsCorrect() {
            ThreadLocalHolder.Accessor accessor = new ThreadLocalHolder.Accessor();
            assertThat(accessor.key()).isEqualTo("tenant-principal");
        }

        @Test
        @DisplayName("Accessor getValue/setValue/setValue() 操作正确")
        void accessorOperations() {
            ThreadLocalHolder.Accessor accessor = new ThreadLocalHolder.Accessor();
            // 初始为 null
            assertThat(accessor.getValue()).isNull();

            // setValue 设置
            accessor.setValue(principal(5001L));
            assertThat(accessor.getValue()).isNotNull();
            assertThat(accessor.getValue().getTenantId()).isEqualTo(5001L);

            // setValue() 无参清除
            accessor.setValue();
            assertThat(accessor.getValue()).isNull();
        }
    }
}
