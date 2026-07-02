package com.richie.component.concurrency.threadpool;

import com.richie.component.concurrency.config.ConcurrencyProperties;
import com.richie.component.concurrency.config.properties.PoolProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.ResolvableType;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.RejectedExecutionHandler;

/**
 * 线程池配置自动刷新器 —— 监听 Spring Cloud {@code EnvironmentChangeEvent}，
 * 自动比对并刷新受影响的 {@link DynamicExecutor} 线程池。
 *
 * <p>当配置中心（Nacos / Apollo / Spring Cloud Config 等）推送配置变更时，Spring 会发布
 * {@code EnvironmentChangeEvent}。本组件拦截该事件，从中提取
 * {@code platform.concurrency.thread-pools.*} 前缀的变更，逐池比对可调整参数
 *（corePoolSize / maximumPoolSize / keepAliveTime / rejectedHandler），
 * 自动调用对应 {@link DynamicExecutor#onResize(PoolResizeEvent)} 完成动态调整。</p>
 *
 * <h3>使用方式</h3>
 * <p>零配置。只要 classpath 中存在 Spring Cloud Context（即
 * {@code EnvironmentChangeEvent} 可用），本组件自动生效。无需任何额外代码。</p>
 *
 * <h3>不可动态调整的参数</h3>
 * <ul>
 *   <li>{@code queueCapacity} — 需新建队列，当前不擦作</li>
 *   <li>{@code threadNamePrefix} — 仅影响新建线程的名称前缀，不影响已有线程</li>
 * </ul>
 *
 * <h3>线程安全</h3>
 * <p>配置快照使用 {@code volatile} + 不可变 {@link Map} 保证事件处理线程可见性。
 * 多次配置变更可在不同线程上安全并发处理。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
public class ThreadPoolConfigRefresher implements ApplicationListener<ApplicationEvent> {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolConfigRefresher.class);

    private static final String CONFIG_PREFIX = "platform.concurrency.thread-pools";

    private static final String ENVIRONMENT_CHANGE_EVENT_CLASS =
            "org.springframework.cloud.context.environment.EnvironmentChangeEvent";

    private final Map<String, DynamicExecutor> executors;

    private final Binder binder;

    /** 上一次应用成功的配置快照。{@code volatile} 保证跨线程可见性。 */
    private volatile Map<String, PoolProperties> snapshot;

    /**
     * 构造配置刷新器。
     *
     * @param executors  全部已注册的 {@link DynamicExecutor} Bean，按 poolName 索引
     * @param binder     用于从最新 {@code Environment} 重新绑定配置的 {@link Binder}
     * @param properties 当前配置属性（用于初始化快照）
     */
    public ThreadPoolConfigRefresher(
            Map<String, DynamicExecutor> executors,
            Binder binder,
            ConcurrencyProperties properties) {
        this.executors = executors;
        this.binder = binder;
        this.snapshot = deepCopy(properties.getThreadPools());
        if (!this.snapshot.isEmpty()) {
            log.info("Thread pool config refresher: initialized with {} pool(s)", this.snapshot.size());
        }
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        String eventClassName = event.getClass().getName();
        if (!ENVIRONMENT_CHANGE_EVENT_CLASS.equals(eventClassName)) {
            return;
        }

        // 提取变更新 key 集合
        Set<String> keys = extractChangedKeys(event);
        if (keys.isEmpty() || keys.stream().noneMatch(k -> k.startsWith(CONFIG_PREFIX))) {
            return;
        }

        log.info("Thread pool config change detected: {} matching key(s)", keys.size());

        // 从最新 Environment 重新绑定完整配置
        Map<String, PoolProperties> latest = readFromEnvironment();
        if (latest == null) {
            log.warn("Failed to re-bind thread-pools config from Environment; skipping refresh");
            return;
        }

        Map<String, PoolProperties> oldSnapshot = this.snapshot;

        // 差分处理
        processNewPools(latest, oldSnapshot);
        processRemovedPools(oldSnapshot, latest);
        processChangedPools(latest, oldSnapshot);

        // 更新快照
        this.snapshot = latest;
    }

    // ========================================================================
    // 内部方法
    // ========================================================================

    @SuppressWarnings("unchecked")
    private Set<String> extractChangedKeys(ApplicationEvent event) {
        try {
            Method getKeys = event.getClass().getMethod("getKeys");
            Object result = getKeys.invoke(event);
            return result instanceof Set ? (Set<String>) result : Collections.emptySet();
        } catch (Exception e) {
            log.warn("Failed to extract changed keys from {}", event.getClass().getName(), e);
            return Collections.emptySet();
        }
    }

    private Map<String, PoolProperties> readFromEnvironment() {
        try {
            var bindable = Bindable.<Map<String, PoolProperties>>of(
                    ResolvableType.forClassWithGenerics(Map.class, String.class, PoolProperties.class));
            return binder.bind(CONFIG_PREFIX, bindable)
                    .orElseGet(HashMap::new);
        } catch (Exception e) {
            log.warn("Failed to bind [{}] from Environment", CONFIG_PREFIX, e);
            return null;
        }
    }

    private void processNewPools(Map<String, PoolProperties> latest, Map<String, PoolProperties> old) {
        for (Map.Entry<String, PoolProperties> entry : latest.entrySet()) {
            String name = entry.getKey();
            if (!old.containsKey(name)) {
                log.warn("Config change: new pool [{}] detected — cannot create DynamicExecutor on the fly; "
                        + "restart required to register", name);
            }
        }
    }

    private void processRemovedPools(Map<String, PoolProperties> old, Map<String, PoolProperties> latest) {
        for (String name : old.keySet()) {
            if (!latest.containsKey(name)) {
                log.warn("Config change: pool [{}] removed from configuration — existing DynamicExecutor "
                        + "will continue running; remove the @Resource injection if no longer needed", name);
            }
        }
    }

    private void processChangedPools(Map<String, PoolProperties> latest, Map<String, PoolProperties> old) {
        for (Map.Entry<String, PoolProperties> entry : latest.entrySet()) {
            String name = entry.getKey();
            PoolProperties newProps = entry.getValue();
            PoolProperties oldProps = old.get(name);
            if (oldProps == null) {
                continue; // 新池已经在 processNewPools 中告警过了
            }
            if (!hasResizeableChange(newProps, oldProps)) {
                continue;
            }
            DynamicExecutor executor = executors.get(name);
            if (executor == null) {
                log.warn("Config change: pool [{}] has config changes but no DynamicExecutor bean found", name);
                continue;
            }
            PoolResizeEvent resizeEvent = buildResizeEvent(newProps, oldProps);
            executor.onResize(resizeEvent);
            log.info("Thread pool [{}] resized: core={}→{}, max={}→{}, keepAlive={}→{}, handler={}→{}",
                    name,
                    oldProps.getCorePoolSize(), newProps.getCorePoolSize(),
                    oldProps.getMaximumPoolSize(), newProps.getMaximumPoolSize(),
                    oldProps.getKeepAliveTime(), newProps.getKeepAliveTime(),
                    oldProps.getRejectedHandler(), newProps.getRejectedHandler());
        }
    }

    /**
     * 判断新旧配置之间是否发生了可动态调整的变化。
     * 忽略 {@code queueCapacity} 和 {@code threadNamePrefix}（无法动态变更）。
     */
    private static boolean hasResizeableChange(PoolProperties newP, PoolProperties oldP) {
        return !Objects.equals(newP.getCorePoolSize(), oldP.getCorePoolSize())
                || !Objects.equals(newP.getMaximumPoolSize(), oldP.getMaximumPoolSize())
                || !Objects.equals(newP.getKeepAliveTime(), oldP.getKeepAliveTime())
                || !Objects.equals(newP.getRejectedHandler(), oldP.getRejectedHandler());
    }

    /**
     * 根据新旧配置差异构建 {@link PoolResizeEvent}，仅填充发生变化的字段。
     */
    private static PoolResizeEvent buildResizeEvent(PoolProperties newP, PoolProperties oldP) {
        PoolResizeEvent.Builder builder = PoolResizeEvent.builder();
        if (!Objects.equals(newP.getCorePoolSize(), oldP.getCorePoolSize())) {
            builder.corePoolSize(newP.getCorePoolSize());
        }
        if (!Objects.equals(newP.getMaximumPoolSize(), oldP.getMaximumPoolSize())) {
            builder.maximumPoolSize(newP.getMaximumPoolSize());
        }
        if (!Objects.equals(newP.getKeepAliveTime(), oldP.getKeepAliveTime())) {
            builder.keepAliveTime(newP.getKeepAliveTime());
        }
        if (!Objects.equals(newP.getRejectedHandler(), oldP.getRejectedHandler())) {
            builder.rejectedHandler(parseRejectedHandler(newP.getRejectedHandler()));
        }
        return builder.build();
    }

    private static Map<String, PoolProperties> deepCopy(Map<String, PoolProperties> source) {
        if (source == null) {
            return new HashMap<>();
        }
        Map<String, PoolProperties> copy = new HashMap<>();
        for (Map.Entry<String, PoolProperties> e : source.entrySet()) {
            PoolProperties p = e.getValue();
            PoolProperties cp = new PoolProperties();
            cp.setCorePoolSize(p.getCorePoolSize());
            cp.setMaximumPoolSize(p.getMaximumPoolSize());
            cp.setKeepAliveTime(p.getKeepAliveTime());
            cp.setQueueCapacity(p.getQueueCapacity());
            cp.setThreadNamePrefix(p.getThreadNamePrefix());
            cp.setRejectedHandler(p.getRejectedHandler());
            copy.put(e.getKey(), cp);
        }
        return copy;
    }

    private static RejectedExecutionHandler parseRejectedHandler(String name) {
        return switch (name.trim().toLowerCase()) {
            case "callerrunspolicy" -> new ThreadPoolExecutor.CallerRunsPolicy();
            case "discardpolicy" -> new ThreadPoolExecutor.DiscardPolicy();
            case "discardoldestpolicy" -> new ThreadPoolExecutor.DiscardOldestPolicy();
            default -> new ThreadPoolExecutor.AbortPolicy();
        };
    }
}
