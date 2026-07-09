/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.tenant.context;

import com.richie.contract.exception.BusinessException;
import com.richie.contract.model.TenantPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TenantContext — 静态门面")
class TenantContextTest {

    private ThreadLocalHolder holder;

    @BeforeEach
    void setUp() {
        holder = new ThreadLocalHolder();
        TenantContext.init(holder);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        // 清除 checker
        TenantContext.setTransactionChecker(null);
    }

    private TenantPrincipal principal(Long id) {
        return new TenantPrincipal().setTenantId(id).setTenantName("tenant-" + id);
    }

    @Nested
    @DisplayName("init / getHolder")
    class InitTests {

        @Test
        @DisplayName("init 后 getHolder 返回注入的 holder")
        void getHolderReturnsInjectedHolder() {
            assertThat(TenantContext.getHolder()).isSameAs(holder);
        }
    }

    @Nested
    @DisplayName("读取 API")
    class ReadApi {

        @Test
        @DisplayName("未绑定时 get() 返回 null")
        void getReturnsNullWhenNotBound() {
            assertThat(TenantContext.get()).isNull();
        }

        @Test
        @DisplayName("getTenantId() 返回绑定租户 ID")
        void getTenantIdReturnsBoundId() {
            TenantContext.runWithTenant(principal(1001L), () -> {
                assertThat(TenantContext.getTenantId()).isEqualTo(1001L);
            });
        }

        @Test
        @DisplayName("getTenantName() 返回绑定租户名称")
        void getTenantNameReturnsBoundName() {
            TenantContext.runWithTenant(principal(1001L), () -> {
                assertThat(TenantContext.getTenantName()).isEqualTo("tenant-1001");
            });
        }

        @Test
        @DisplayName("未绑定时 getTenantId() 返回 null")
        void getTenantIdReturnsNullWhenNotBound() {
            assertThat(TenantContext.getTenantId()).isNull();
        }

        @Test
        @DisplayName("未绑定时 getTenantName() 返回 null")
        void getTenantNameReturnsNullWhenNotBound() {
            assertThat(TenantContext.getTenantName()).isNull();
        }
    }

    @Nested
    @DisplayName("require API")
    class RequireApi {

        @Test
        @DisplayName("已绑定时 require() 返回 principal")
        void requireReturnsPrincipalWhenBound() {
            TenantContext.runWithTenant(principal(2001L), () -> {
                assertThat(TenantContext.require().getTenantId()).isEqualTo(2001L);
            });
        }

        @Test
        @DisplayName("未绑定时 require() 抛出 BusinessException")
        void requireThrowsWhenNotBound() {
            assertThatThrownBy(TenantContext::require)
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("已绑定时 requireTenantId() 返回 ID")
        void requireTenantIdReturnsId() {
            TenantContext.runWithTenant(principal(3001L), () -> {
                assertThat(TenantContext.requireTenantId()).isEqualTo(3001L);
            });
        }

        @Test
        @DisplayName("未绑定时 requireTenantId() 抛出异常")
        void requireTenantIdThrowsWhenNotBound() {
            assertThatThrownBy(TenantContext::requireTenantId)
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("runWithTenant")
    class RunWithTenant {

        @Test
        @DisplayName("Runnable 版本绑定并执行")
        void runnableBindsAndExecutes() {
            AtomicBoolean executed = new AtomicBoolean(false);
            TenantContext.runWithTenant(principal(4001L), () -> executed.set(true));
            assertThat(executed.get()).isTrue();
        }

        @Test
        @DisplayName("Supplier 版本绑定并返回结果")
        void supplierBindsAndReturnsResult() {
            Long result = TenantContext.runWithTenant(principal(5001L),
                    TenantContext::getTenantId);
            assertThat(result).isEqualTo(5001L);
        }

        @Test
        @DisplayName("嵌套 runWithTenant 恢复外层")
        void nestingRestoresOuter() {
            TenantContext.runWithTenant(principal(100L), () -> {
                assertThat(TenantContext.getTenantId()).isEqualTo(100L);
                TenantContext.runWithTenant(principal(200L), () -> {
                    assertThat(TenantContext.getTenantId()).isEqualTo(200L);
                });
                assertThat(TenantContext.getTenantId()).isEqualTo(100L);
            });
        }
    }

    @Nested
    @DisplayName("TransactionTenantChecker")
    class CheckerTests {

        @Test
        @DisplayName("注册 checker 后 runWithTenant 触发检查")
        void checkerIsInvokedOnRunWithTenant() {
            AtomicBoolean checked = new AtomicBoolean(false);
            TenantContext.setTransactionChecker(targetTenantId -> checked.set(true));
            TenantContext.runWithTenant(principal(7001L), () -> {});
            assertThat(checked.get()).isTrue();
        }

        @Test
        @DisplayName("checker 抛异常时 runWithTenant 传播异常")
        void checkerExceptionPropagates() {
            TenantContext.setTransactionChecker(targetTenantId -> {
                throw new RuntimeException("frozen");
            });
            assertThatThrownBy(() -> TenantContext.runWithTenant(principal(8001L), () -> {}))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("frozen");
        }
    }

    @Nested
    @DisplayName("clear")
    class ClearTests {

        @Test
        @DisplayName("clear 后 get 返回 null")
        void clearRemovesBinding() {
            holder.set(principal(9001L));
            assertThat(TenantContext.getTenantId()).isEqualTo(9001L);
            TenantContext.clear();
            assertThat(TenantContext.get()).isNull();
        }
    }
}
