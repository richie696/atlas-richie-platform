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
package com.richie.component.tenant.cross;

import com.richie.component.tenant.context.ScopedValueHolder;
import com.richie.component.tenant.context.TenantContext;
import com.richie.contract.model.TenantPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TenantStructuredTaskScope — StructuredTaskScope 工厂")
class TenantStructuredTaskScopeTest {

    @BeforeEach
    void setUp() {
        TenantContext.init(new ScopedValueHolder());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("awaitAll — 全部成功 / 任一失败")
    class AwaitAll {

        @Test
        @DisplayName("全部子任务成功时,父任务可见各子任务结果")
        void allSucceed() throws Exception {
            try (var scope = TenantStructuredTaskScope.<Integer>awaitAll()) {
                var f1 = scope.fork(() -> 100);
                var f2 = scope.fork(() -> 200);
                var f3 = scope.fork(() -> 300);
                scope.join();
                assertThat(f1.get()).isEqualTo(100);
                assertThat(f2.get()).isEqualTo(200);
                assertThat(f3.get()).isEqualTo(300);
            }
        }

        @Test
        @DisplayName("任一子任务抛异常时,scope.join() 透传异常")
        void failurePropagates() {
            assertThatThrownBy(() -> {
                try (var scope = TenantStructuredTaskScope.<Integer>awaitAll()) {
                    var f1 = scope.fork(() -> 100);
                    var f2 = scope.fork(() -> {
                        throw new RuntimeException("task2 failed");
                    });
                    var f3 = scope.fork(() -> 300);
                    scope.join();
                }
            }).hasMessageContaining("task2 failed");
        }
    }

    @Nested
    @DisplayName("anySuccessful — 任一成功")
    class AnySuccessful {

        @Test
        @DisplayName("首个成功的子任务结果被返回")
        void firstSuccessReturned() throws Exception {
            try (var scope = TenantStructuredTaskScope.<String>anySuccessful()) {
                var f1 = scope.fork(() -> {
                    Thread.sleep(100);
                    return "slow";
                });
                var f2 = scope.fork(() -> "fast");
                scope.join();
                assertThat(f2.get()).isEqualTo("fast");
            }
        }
    }

    @Nested
    @DisplayName("租户作用域集成")
    class TenantScopeIntegration {

        @Test
        @DisplayName("父任务绑定租户时,子任务可见同一租户 ID")
        void tenantVisibleInChild() throws Exception {
            TenantPrincipal principal = new TenantPrincipal().setTenantId(1234L);
            AtomicReference<Long> childTenantId = new AtomicReference<>();
            AtomicReference<Long> childThreadIsFork = new AtomicReference<>();

            TenantContext.runWithTenant(principal, () -> {
                try (var scope = TenantStructuredTaskScope.<Long>awaitAll()) {
                    var subtask = scope.fork(() -> {
                        childThreadIsFork.set(Thread.currentThread().isVirtual() ? 1L : 0L);
                        return TenantContext.getTenantId();
                    });
                    scope.join();
                    childTenantId.set(subtask.get());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            assertThat(childTenantId.get()).isEqualTo(1234L);
        }

        @Test
        @DisplayName("子任务抛异常时,父任务捕获并包装")
        void exceptionInChildCaughtByParent() {
            assertThatThrownBy(() -> {
                try (var scope = TenantStructuredTaskScope.<String>awaitAll()) {
                    scope.fork(() -> {
                        throw new IllegalStateException("child error");
                    });
                    scope.join();
                }
            }).hasMessageContaining("child error");
        }
    }

    @Nested
    @DisplayName("工厂语义")
    class FactorySemantics {

        @Test
        @DisplayName("两个 awaitAll() 返回不同实例")
        void instancesAreIndependent() {
            try (var s1 = TenantStructuredTaskScope.<Integer>awaitAll();
                 var s2 = TenantStructuredTaskScope.<Integer>awaitAll()) {
                assertThat(s1).isNotSameAs(s2);
            }
        }

        @Test
        @DisplayName("scope 关闭后无法再 fork")
        void closedScopeRejectsFork() {
            var scope = TenantStructuredTaskScope.<Integer>awaitAll();
            scope.close();
            assertThatThrownBy(() -> scope.fork(() -> 1))
                .isInstanceOf(IllegalStateException.class);
        }
    }
}
