package com.richie.component.tenant.context;

import com.richie.component.tenant.exception.TenantSwitchInTransactionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TransactionTenantHolder — 事务租户冻结检测")
class TransactionTenantHolderTest {

    @AfterEach
    void tearDown() {
        TransactionTenantHolder.clear();
    }

    @Nested
    @DisplayName("freeze / get / clear")
    class FreezeGetClear {

        @Test
        @DisplayName("未冻结时 get() 返回 null")
        void getReturnsNullWhenNotFrozen() {
            assertThat(TransactionTenantHolder.get()).isNull();
        }

        @Test
        @DisplayName("freeze 后 get 返回冻结值")
        void freezeAndGet() {
            TransactionTenantHolder.freeze(1001L);
            assertThat(TransactionTenantHolder.get()).isEqualTo(1001L);
        }

        @Test
        @DisplayName("clear 后 get 返回 null")
        void clearRemovesFrozenValue() {
            TransactionTenantHolder.freeze(1001L);
            TransactionTenantHolder.clear();
            assertThat(TransactionTenantHolder.get()).isNull();
        }
    }

    @Nested
    @DisplayName("checkSwitch")
    class CheckSwitchTests {

        @Test
        @DisplayName("未冻结时 checkSwitch 不抛异常")
        void noFreezeCheckSwitchPasses() {
            TransactionTenantHolder.checkSwitch(2001L);
            // no exception
        }

        @Test
        @DisplayName("同一租户 checkSwitch 不抛异常")
        void sameTenantCheckSwitchPasses() {
            TransactionTenantHolder.freeze(1001L);
            TransactionTenantHolder.checkSwitch(1001L);
            // no exception
        }

        @Test
        @DisplayName("不同租户 checkSwitch 抛 TenantSwitchInTransactionException")
        void differentTenantCheckSwitchThrows() {
            TransactionTenantHolder.freeze(1001L);
            assertThatThrownBy(() -> TransactionTenantHolder.checkSwitch(2001L))
                    .isInstanceOf(TenantSwitchInTransactionException.class)
                    .satisfies(ex -> {
                        TenantSwitchInTransactionException e = (TenantSwitchInTransactionException) ex;
                        assertThat(e.getFromTenantId()).isEqualTo(1001L);
                        assertThat(e.getToTenantId()).isEqualTo(2001L);
                    });
        }
    }
}
