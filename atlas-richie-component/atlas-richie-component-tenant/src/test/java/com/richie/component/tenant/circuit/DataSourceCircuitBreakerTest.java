package com.richie.component.tenant.circuit;

import com.richie.component.tenant.config.MultiTenancyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DataSourceCircuitBreaker — 数据源熔断器状态机")
class DataSourceCircuitBreakerTest {

    private MultiTenancyProperties props;
    private DataSourceCircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        props = new MultiTenancyProperties();
        // 降低阈值便于测试
        props.getCircuit().setFailureThreshold(3);
        props.getCircuit().setOpenWindowMs(100); // 100ms 快速超时
        breaker = new DataSourceCircuitBreaker(props);
    }

    @Nested
    @DisplayName("初始状态")
    class InitialState {

        @Test
        @DisplayName("未注册 key 时 isOpen 返回 false")
        void unknownKeyIsNotOpen() {
            assertThat(breaker.isOpen("unknown")).isFalse();
        }

        @Test
        @DisplayName("未注册 key 时 getStatus 返回 CLOSED")
        void unknownKeyStatusIsClosed() {
            assertThat(breaker.getStatus("unknown")).isEqualTo(DataSourceCircuitBreaker.CircuitStatus.CLOSED);
        }
    }

    @Nested
    @DisplayName("CLOSED → OPEN")
    class ClosedToOpen {

        @Test
        @DisplayName("连续失败达阈值后转为 OPEN")
        void failuresExceedThresholdOpensCircuit() {
            breaker.recordFailure("ds-1");
            breaker.recordFailure("ds-1");
            assertThat(breaker.isOpen("ds-1")).isFalse();

            breaker.recordFailure("ds-1");
            assertThat(breaker.isOpen("ds-1")).isTrue();
            assertThat(breaker.getStatus("ds-1")).isEqualTo(DataSourceCircuitBreaker.CircuitStatus.OPEN);
        }
    }

    @Nested
    @DisplayName("OPEN → HALF_OPEN")
    class OpenToHalfOpen {

        @Test
        @DisplayName("超过 openWindowMs 后转为 HALF_OPEN")
        void afterTimeoutTransitionsToHalfOpen() throws Exception {
            breaker.recordFailure("ds-2");
            breaker.recordFailure("ds-2");
            breaker.recordFailure("ds-2");
            assertThat(breaker.isOpen("ds-2")).isTrue();

            // 等待超过 openWindowMs (100ms)
            Thread.sleep(150);

            assertThat(breaker.isOpen("ds-2")).isFalse();
            assertThat(breaker.getStatus("ds-2")).isEqualTo(DataSourceCircuitBreaker.CircuitStatus.HALF_OPEN);
        }
    }

    @Nested
    @DisplayName("HALF_OPEN → CLOSED")
    class HalfOpenToClosed {

        @Test
        @DisplayName("HALF_OPEN 状态下 recordSuccess 转为 CLOSED")
        void successInHalfOpenClosesCircuit() throws Exception {
            breaker.recordFailure("ds-3");
            breaker.recordFailure("ds-3");
            breaker.recordFailure("ds-3");
            Thread.sleep(150);
            // isOpen() 触发 OPEN → HALF_OPEN 转换
            assertThat(breaker.isOpen("ds-3")).isFalse();
            assertThat(breaker.getStatus("ds-3")).isEqualTo(DataSourceCircuitBreaker.CircuitStatus.HALF_OPEN);

            breaker.recordSuccess("ds-3");
            assertThat(breaker.getStatus("ds-3")).isEqualTo(DataSourceCircuitBreaker.CircuitStatus.CLOSED);
        }
    }

    @Nested
    @DisplayName("回调")
    class Callbacks {

        @Test
        @DisplayName("onOpen 回调在 OPEN 时触发")
        void onOpenCallbackFires() {
            AtomicReference<String> openedKey = new AtomicReference<>();
            breaker.onOpen(openedKey::set);

            breaker.recordFailure("ds-4");
            breaker.recordFailure("ds-4");
            breaker.recordFailure("ds-4");

            assertThat(openedKey.get()).isEqualTo("ds-4");
        }

        @Test
        @DisplayName("onClose 回调在 CLOSED 时触发")
        void onCloseCallbackFires() throws Exception {
            AtomicReference<String> closedKey = new AtomicReference<>();
            breaker.onClose(closedKey::set);

            breaker.recordFailure("ds-5");
            breaker.recordFailure("ds-5");
            breaker.recordFailure("ds-5");
            Thread.sleep(150);
            // isOpen() 触发 OPEN → HALF_OPEN
            breaker.isOpen("ds-5");
            breaker.recordSuccess("ds-5");

            assertThat(closedKey.get()).isEqualTo("ds-5");
        }
    }

    @Nested
    @DisplayName("getAllStatuses")
    class AllStatuses {

        @Test
        @DisplayName("返回所有已注册数据源的状态快照")
        void returnsAllStatuses() {
            breaker.recordFailure("ds-a");
            breaker.recordFailure("ds-b");
            breaker.recordFailure("ds-b");
            breaker.recordFailure("ds-b");

            Map<String, DataSourceCircuitBreaker.CircuitStatusSnapshot> all = breaker.getAllStatuses();
            assertThat(all).containsKeys("ds-a", "ds-b");
            assertThat(all.get("ds-b").status()).isEqualTo("OPEN");
        }
    }
}
