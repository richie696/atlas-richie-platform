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
package com.richie.component.vector.operations;

import com.richie.component.vector.config.VectorFacadeProperties;
import com.richie.component.vector.service.VectorService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 跨 provider 调度门面 — 提供重试、回退与可观测性能力。
 * <p>
 * 业务方通过 {@link #execute(String, Function)} 调用任意 {@link VectorService} 操作：
 * <ol>
 *   <li>在 <b>主 provider</b> 上重试（指数退避）</li>
 *   <li>若主 provider 全部重试仍失败，依次尝试 <b>回退链</b>中的 provider</li>
 *   <li>所有 provider 均失败时，抛出聚合异常，包含每个 provider 的最后错误</li>
 * </ol>
 *
 * <h2>典型用法</h2>
 * <pre>{@code
 * String id = vectorOperationsFacade.execute("addText",
 *         svc -> svc.addText("docs", "hello world", Map.of()));
 * }</pre>
 *
 * <h2>线程安全</h2>
 * 所有 provider 注册信息在构造后不可变；{@link MeterRegistry} 的指标注册自身是线程安全的。
 *
 * @author richie696
 * @since 2.1.0
 */
@Slf4j
public class VectorOperationsFacade {

    private final Map<String, VectorService> providers;
    private final VectorFacadeProperties props;
    private final MeterRegistry meterRegistry;

    /**
     * 缓存已注册的 Timer/Counter，避免每次调用都重建（MeterRegistry 内部已缓存，本层仅做轻量包装）。
     * <p>
     * 实际指标对象由 MeterRegistry 持有 — 此缓存仅记录"已经用过"，用于可选的批量预热等场景。
     */
    private final ConcurrentHashMap<String, Timer> timerCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> counterCache = new ConcurrentHashMap<>();

    public VectorOperationsFacade(Map<String, VectorService> providers,
                                  VectorFacadeProperties props,
                                  ObjectProvider<MeterRegistry> meterRegistry) {
        // 防御性拷贝 + 保证 bean name 不为 null
        Map<String, VectorService> snapshot = new LinkedHashMap<>();
        if (providers != null) {
            providers.forEach((name, svc) -> {
                if (name != null && svc != null) {
                    snapshot.put(name, svc);
                }
            });
        }
        this.providers = Collections.unmodifiableMap(snapshot);
        this.props = props != null ? props : new VectorFacadeProperties();
        this.meterRegistry = meterRegistry != null ? meterRegistry.getIfAvailable() : null;
    }

    // ==================== 访问器 ====================

    /**
     * 返回主 provider，未配置主 provider 或主 provider 不在已注册列表时抛 {@link IllegalStateException}。
     */
    public VectorService primary() {
        String name = props.getDefaultProvider();
        VectorService svc = providers.get(name);
        if (svc == null) {
            throw new IllegalStateException(
                    "默认 provider 未注册: " + name + "。已注册: " + providers.keySet());
        }
        return svc;
    }

    /**
     * 按 bean name 获取 provider。
     *
     * @throws IllegalArgumentException provider 不存在时
     */
    public VectorService get(String providerName) {
        VectorService svc = providers.get(providerName);
        if (svc == null) {
            throw new IllegalArgumentException(
                    "provider 未注册: " + providerName + "。已注册: " + providers.keySet());
        }
        return svc;
    }

    /**
     * 返回所有已注册 provider 的 bean name（不可变视图）。
     */
    public List<String> providerNames() {
        return List.copyOf(providers.keySet());
    }

    // ==================== 主调度 ====================

    /**
     * 在主 provider 上执行操作；主 provider 失败时按回退链顺序尝试其他 provider。
     *
     * @param operation 操作名称（用于指标 tag 与日志）
     * @param action    实际调用 VectorService 的逻辑
     * @param <T>       返回类型
     * @return action 的执行结果
     * @throws VectorFacadeExecutionException 所有 provider 均失败时抛出，聚合各 provider 的最后错误
     */
    public <T> T execute(String operation, Function<VectorService, T> action) {
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("operation 不能为空");
        }
        if (action == null) {
            throw new IllegalArgumentException("action 不能为空");
        }

        // 构建尝试序列：主 provider → fallback chain 中已注册的 provider
        List<String> sequence = buildAttemptSequence();

        List<ProviderFailure> failures = new ArrayList<>();
        for (String providerName : sequence) {
            VectorService svc = providers.get(providerName);
            if (svc == null) {
                continue;
            }
            try {
                return invokeWithRetry(providerName, operation, action, svc);
            } catch (Exception e) {
                // invokeWithRetry 抛出 VectorFacadeExecutionException 包装了最后一次真实异常，
                // 此处解包以保留原始 cause（业务方依赖 ProviderFailure.cause() 拿到根因，
                // 例如 VectorOperationsFacadeTest 的 aggregatedException 用例）。
                Throwable rootCause = (e instanceof VectorFacadeExecutionException && e.getCause() != null)
                        ? e.getCause()
                        : e;
                failures.add(new ProviderFailure(providerName, rootCause));
                log.warn("VectorOperationsFacade provider={} operation={} 失败，转入下一个 provider",
                        providerName, operation, e);
            }
        }

        throw new VectorFacadeExecutionException(
                "所有 provider 均失败: operation=" + operation, failures);
    }

    /**
     * 在指定 provider 上执行，重试 + 指数退避。
     */
    private <T> T invokeWithRetry(String providerName,
                                  String operation,
                                  Function<VectorService, T> action,
                                  VectorService svc) {
        int maxRetries = Math.max(0, props.getMaxRetries());
        long baseBackoff = Math.max(0L, props.getRetryBackoffMillis());
        Exception lastError = null;

        // 总尝试次数 = maxRetries + 1
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            Timer.Sample sample = startTimer();
            try {
                T result = action.apply(svc);
                recordSuccess(sample, providerName, operation);
                return result;
            } catch (Exception e) {
                lastError = e;
                recordFailure(sample, providerName, operation, e);
                if (attempt < maxRetries) {
                    long sleepMillis = computeBackoff(baseBackoff, attempt);
                    if (sleepMillis > 0) {
                        try {
                            Thread.sleep(sleepMillis);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new VectorFacadeExecutionException(
                                    "重试被中断: provider=" + providerName,
                                    List.of(new ProviderFailure(providerName, ie)));
                        }
                    }
                }
            }
        }

        throw new VectorFacadeExecutionException(
                "provider " + providerName + " 重试 " + maxRetries + " 次仍失败",
                lastError,
                List.of(new ProviderFailure(providerName, lastError)));
    }

    /**
     * 构建尝试序列：[主 provider, fallback[0], fallback[1], ...]，去重并保留顺序。
     */
    private List<String> buildAttemptSequence() {
        List<String> sequence = new ArrayList<>();
        String primary = props.getDefaultProvider();
        if (primary != null && !primary.isBlank()) {
            sequence.add(primary);
        }
        List<String> chain = props.getFallbackChain();
        if (chain != null) {
            for (String name : chain) {
                if (name != null && !name.isBlank() && !sequence.contains(name)) {
                    sequence.add(name);
                }
            }
        }
        return sequence;
    }

    // ==================== 退避 ====================

    /**
     * 指数退避：{@code base * 2^attempt}。attempt 从 0 开始。
     * <p>
     * 同时防御 {@code base} 过大导致的 {@link ArithmeticException}。
     */
    static long computeBackoff(long base, int attempt) {
        if (base <= 0 || attempt < 0) {
            return 0L;
        }
        if (attempt >= 30) {
            // 避免位移/乘法溢出 — 上限约 base * 2^30
            return Long.MAX_VALUE / 2;
        }
        long multiplier = 1L << attempt;
        if (base > Long.MAX_VALUE / multiplier) {
            return Long.MAX_VALUE / 2;
        }
        return base * multiplier;
    }

    // ==================== 可观测性 ====================

    private Timer.Sample startTimer() {
        if (meterRegistry == null) {
            return null;
        }
        return Timer.start(meterRegistry);
    }

    private void recordSuccess(Timer.Sample sample, String provider, String operation) {
        if (sample == null || meterRegistry == null) {
            return;
        }
        Timer timer = timerCache.computeIfAbsent(provider + "|" + operation, k -> Timer.builder(
                        "vector.facade.operation")
                .description("VectorOperationsFacade 调度耗时")
                .tags(Tags.of("provider", provider, "operation", operation))
                .publishPercentileHistogram(false)
                .register(meterRegistry));
        sample.stop(timer);
    }

    private void recordFailure(Timer.Sample sample, String provider, String operation, Exception error) {
        if (meterRegistry == null) {
            return;
        }
        if (sample != null) {
            Timer timer = timerCache.computeIfAbsent(provider + "|" + operation, k -> Timer.builder(
                            "vector.facade.operation")
                    .description("VectorOperationsFacade 调度耗时")
                    .tags(Tags.of("provider", provider, "operation", operation))
                    .publishPercentileHistogram(false)
                    .register(meterRegistry));
            sample.stop(timer);
        }
        String failureKey = provider + "|" + operation + "|" + error.getClass().getSimpleName();
        Counter counter = counterCache.computeIfAbsent(failureKey, k -> Counter.builder("vector.facade.failure")
                .description("VectorOperationsFacade 调用失败计数")
                .tags(Tags.of(
                        "provider", provider,
                        "operation", operation,
                        "exception", error.getClass().getSimpleName()))
                .register(meterRegistry));
        counter.increment();
    }

    /**
     * 单个 provider 调用的失败记录 — facade 内部跨 provider 重试时收集所有失败最终聚合抛出。
     *
     * @param provider provider 名称（与 bean name 一致）
     * @param cause    该 provider 最后一次调用的根因异常
     */
    public record ProviderFailure(String provider, Throwable cause) {
    }

    /**
     * 所有 provider 均失败时抛出的聚合异常 — 携带每个 provider 的最后错误，业务方通过 {@link #getFailures()} 获取。
     */
    public static class VectorFacadeExecutionException extends RuntimeException {

        private final List<ProviderFailure> failures;

        public VectorFacadeExecutionException(String message, List<ProviderFailure> failures) {
            super(message);
            this.failures = failures == null ? List.of() : List.copyOf(failures);
        }

        public VectorFacadeExecutionException(String message, Throwable cause, List<ProviderFailure> failures) {
            super(message, cause);
            this.failures = failures == null ? List.of() : List.copyOf(failures);
        }

        public List<ProviderFailure> getFailures() {
            return failures;
        }
    }
}
