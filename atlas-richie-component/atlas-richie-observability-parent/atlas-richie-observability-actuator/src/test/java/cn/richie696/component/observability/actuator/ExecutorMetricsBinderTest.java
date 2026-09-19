package cn.richie696.component.observability.actuator;

import cn.richie696.component.observability.core.ObservabilityState;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutorMetricsBinderTest {

    @Test
    void bindsLowCardinalityThreadPoolMetrics() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 2, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            new ExecutorMetricsBinder(
                    Map.of("agent.worker", executor),
                    ObservabilityState.enabledState()).bindTo(registry);

            assertThat(registry.get("executor.active")
                    .tag("pool_name", "agent.worker").gauge().value()).isZero();
            assertThat(registry.get("executor.queue.size")
                    .tag("pool_name", "agent.worker").gauge().value()).isZero();
            assertThat(registry.get("executor.pool.max")
                    .tag("pool_name", "agent.worker").gauge().value()).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void disabledStateDoesNotRegisterExecutorMetrics() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            new ExecutorMetricsBinder(
                    Map.of("agent.worker", executor),
                    ObservabilityState.disabledState()).bindTo(registry);
            assertThat(registry.find("executor.active").meter()).isNull();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void bindsRejectedCountWhenApplicationOptsIntoCountingHandler() throws InterruptedException {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1));
        executor.setRejectedExecutionHandler(new ObservabilityRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy()));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            new ExecutorMetricsBinder(
                    Map.of("agent.worker", executor),
                    ObservabilityState.enabledState()).bindTo(registry);
            executor.execute(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
            started.await();
            executor.execute(() -> { });
            executor.execute(() -> { });
        } catch (RejectedExecutionException ignored) {
            // The delegate policy intentionally rejects the third task.
        } finally {
            release.countDown();
            assertThat(registry.get("executor.rejected")
                    .tag("pool_name", "agent.worker").gauge().value()).isEqualTo(1);
            executor.shutdownNow();
        }
    }
}
