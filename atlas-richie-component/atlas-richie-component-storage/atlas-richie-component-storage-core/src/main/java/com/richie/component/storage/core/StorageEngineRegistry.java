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
package com.richie.component.storage.core;

import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.enums.StorageEngineEnum;
import com.richie.component.storage.observability.StorageEngineMetrics;
import com.richie.context.common.api.SpringContextHolder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 存储引擎注册中心（多引擎并存模式）
 * <p>
 * 管理多种类型的引擎实例，支持运行时热切换。
 * 每种引擎类型维护独立的 Proxy，互不影响。
 * <p>
 * 典型场景：对象存储（业务数据） + FTP/SMB（系统间数据交换）同时运行。
 * <p>
 * Provider 不通过构造函数注入，而是运行时通过 {@link SpringContextHolder} 动态查找，
 * 确保手动模式下也能发现后续注册的 Provider Bean。
 *
 * @author richie696
 * @version 1.0
 * @since 2024-01-01
 */
@Slf4j
public class StorageEngineRegistry {

    private final StorageEngineMetrics metrics = new StorageEngineMetrics();

    /**
     * 返回当前实例的内部计数器（仅供 {@link com.richie.component.storage.observability.StorageMetricsBinder} 绑定 Micrometer 使用）。
     */
    public StorageEngineMetrics getMetrics() {
        return metrics;
    }


    /**
     * 每种引擎类型的代理持有者，支持多引擎并存
     */
    private final Map<StorageEngineEnum, ProxyHolder> engineProxies = new ConcurrentHashMap<>();

    /**
     * 对象存储统一代理（所有对象存储引擎共享 objectStorageEngine 限定符）
     * <p>
     * 8 种对象存储（MinIO/OSS/COS/OBS/S3/KS3/TOS/Azure）共用此代理，
     * 切换对象存储类型时自动更新 delegate。
     */
    private final ProxyHolder objectProxy;

    /**
     * 默认引擎类型（Proxy @Primary 委托目标）
     */
    @Getter
    private volatile StorageEngineEnum defaultEngineType;

    /**
     * 默认引擎 ID
     */
    @Getter
    private volatile String defaultEngineId;

    public StorageEngineRegistry() {
        // 为所有引擎类型预创建 ProxyHolder，确保 getProxy() 在任何时刻都可用
        for (StorageEngineEnum type : StorageEngineEnum.values()) {
            engineProxies.put(type, new ProxyHolder(type));
        }
        // 对象存储统一代理（独立于具体引擎类型，所有对象存储共享）
        objectProxy = new ProxyHolder(null);
        log.info("StorageEngineRegistry 初始化，已预创建 {} 种引擎代理", StorageEngineEnum.values().length);
    }

    /**
     * 通过 SpringContextHolder 动态查找所有已注册的 Provider Bean
     */
    private List<StorageEngineProvider> getProviders() {
        Map<String, StorageEngineProvider> beanMap =
                SpringContextHolder.getApplicationContext()
                        .getBeansOfType(StorageEngineProvider.class);
        return new ArrayList<>(beanMap.values());
    }

    /**
     * 注册初始引擎（启动时由自动配置调用，将 @Service Bean 绑定到对应类型的 Proxy）
     * <p>
     * 线程安全：synchronized 与 {@link #switchEngine} 共享同一把锁，
     * 避免并发场景下重复注册同一个引擎类型。
     *
     * @param engineType 引擎类型
     * @param engineId   引擎 ID
     * @param engine     Spring 管理的引擎实例
     */
    public synchronized void registerInitialEngine(StorageEngineEnum engineType, String engineId,
                                                   StorageEngine engine) {
        ProxyHolder holder = engineProxies.get(engineType);
        if (holder == null) {
            throw new IllegalStateException("未知的引擎类型: " + engineType);
        }
        if (holder.delegate != null) {
            throw new IllegalStateException("引擎类型 [" + engineType + "] 已注册，请勿重复注册");
        }
        holder.delegate = engine;

        // 对象存储引擎同步更新 objectProxy
        if (engineType.isObjectStorage()) {
            objectProxy.delegate = engine;
        }

        // 第一个注册的引擎作为默认引擎
        if (defaultEngineType == null) {
            this.defaultEngineType = engineType;
            this.defaultEngineId = engineId;
        }
        metrics.incrementRegister(engineType);
        log.info("注册初始存储引擎: type={}, id={}, engine={}",
                engineType, engineId, engine.getClass().getSimpleName());
    }

    /**
     * 注册或切换指定类型的引擎（管理后台调用）
     * <p>
     * 线程安全：synchronized 保证同一时刻只有一个切换操作在执行。
     * 只影响指定类型的引擎，不影响其他类型。
     * <p>
     * 等同于 {@code switchEngine(engineType, properties, "system", null)}。
     *
     * @param engineType 引擎类型
     * @param properties 存储配置属性（Provider 从中提取所需子配置）
     * @return 新创建的引擎实例
     */
    public StorageEngine switchEngine(StorageEngineEnum engineType,
                                       StorageProperties properties) {
        return switchEngine(engineType, properties, "system", null);
    }

    /**
     * 注册或切换指定类型的引擎（带审计上下文）
     * <p>
     * 线程安全：synchronized 保证同一时刻只有一个切换操作在执行。
     * actor 与 reason 会写入日志，便于追溯"谁在什么理由下切换了引擎"。
     *
     * @param engineType 引擎类型
     * @param properties 存储配置属性
     * @param actor      触发本次切换的主体（用户 ID / "system" / "scheduler:xxx" 等）；可为 null
     * @param reason     切换原因（如 "manual override"）；可为 null
     * @return 新创建的引擎实例
     */
    public synchronized StorageEngine switchEngine(StorageEngineEnum engineType,
                                                    StorageProperties properties,
                                                    String actor, String reason) {
        Objects.requireNonNull(engineType, "engineType must not be null");
        Objects.requireNonNull(properties, "properties must not be null");

        ProxyHolder holder = engineProxies.get(engineType);
        if (holder == null) {
            throw new IllegalStateException("未知的引擎类型: " + engineType);
        }

        String newEngineId = engineType.getConfigValue() + "-" +
                UUID.randomUUID().toString().substring(0, 8);

        log.info("开始{}存储引擎: type={}, id={}, actor={}, reason={}",
                holder.delegate != null ? "切换" : "注册",
                engineType, newEngineId, actor, reason);

        // 1. 通过 SpringContextHolder 动态查找 Provider
        List<StorageEngineProvider> providers = getProviders();
        StorageEngineProvider provider = providers.stream()
                .filter(p -> p.supportedEngineType() == engineType)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "未找到引擎类型 [" + engineType + "] 的 Provider，" +
                        "请确认对应引擎模块已引入 classpath"));

        // 2. 校验参数
        provider.validate(properties);

        // 3. 创建新引擎（先创建，确保可用后再销毁旧的）
        StorageEngine newEngine = provider.create(properties);

        // 4. 初始化新引擎（手动模式下替代 @PostConstruct）
        provider.afterPropertiesSet(newEngine);

        // 5. 销毁旧引擎（通过 Provider 清理连接池等资源）
        StorageEngine oldEngine = holder.delegate;
        if (oldEngine != null) {
            provider.destroy(oldEngine);
        }

        // 6. 更新引用（volatile 保证可见性）
        holder.delegate = newEngine;

        // 7. 对象存储引擎同步更新 objectProxy
        if (engineType.isObjectStorage()) {
            objectProxy.delegate = newEngine;
        }

        // 8. 如果是第一个引擎或替换的是默认引擎，更新默认
        if (defaultEngineType == null || defaultEngineType == engineType) {
            this.defaultEngineType = engineType;
            this.defaultEngineId = newEngineId;
        }

        log.info("存储引擎{}完成: type={}, id={}, engine={}, actor={}, reason={}",
                oldEngine != null ? "切换" : "注册",
                engineType, newEngineId, newEngine.getClass().getSimpleName(),
                actor, reason);

        metrics.incrementSwitch(engineType);
        return newEngine;
    }

    /**
     * 刷新指定类型的引擎（强制替换已初始化的实例；保留旧 delegate 失败时回滚）
     * <p>
     * 与 {@link #switchEngine} 的区别：
     * <ul>
     *     <li>仅当当前已有引擎时才能刷新，否则抛 {@link IllegalStateException}</li>
     *     <li>采用"先建后拆"的回滚策略：新引擎 {@code afterPropertiesSet} 成功后才替换旧 delegate</li>
     *     <li>旧引擎 {@code destroy} 失败仅记录 warn，不影响新引擎生效，避免外部资源被拆但旧引用残留</li>
     * </ul>
     */
    public StorageEngine refreshEngine(StorageEngineEnum engineType,
                                        StorageProperties properties) {
        return refreshEngine(engineType, properties, "system", null);
    }

    /**
     * 刷新指定类型的引擎（带审计上下文 + 回滚语义）
     *
     * @param engineType 引擎类型
     * @param properties 存储配置属性
     * @param actor      触发本次刷新的主体；可为 null
     * @param reason     刷新原因；可为 null
     * @return 新创建的引擎实例
     * @throws IllegalStateException 当引擎类型未知或当前未初始化时
     */
    public synchronized StorageEngine refreshEngine(StorageEngineEnum engineType,
                                                    StorageProperties properties,
                                                    String actor, String reason) {
        Objects.requireNonNull(engineType, "engineType must not be null");
        Objects.requireNonNull(properties, "properties must not be null");

        ProxyHolder holder = engineProxies.get(engineType);
        if (holder == null) {
            throw new IllegalStateException("未知的引擎类型: " + engineType);
        }
        if (holder.delegate == null) {
            throw new IllegalStateException(
                    "无法刷新尚未初始化的引擎类型: " + engineType +
                            "，请先调用 registerInitialEngine 或 switchEngine");
        }

        String newEngineId = engineType.getConfigValue() + "-" +
                UUID.randomUUID().toString().substring(0, 8);
        log.info("开始刷新存储引擎: type={}, id={}, actor={}, reason={}",
                engineType, newEngineId, actor, reason);

        StorageEngineProvider provider = findProvider(engineType);
        provider.validate(properties);

        StorageEngine newEngine = provider.create(properties);
        try {
            provider.afterPropertiesSet(newEngine);
        } catch (RuntimeException initEx) {
            try {
                provider.destroy(newEngine);
            } catch (RuntimeException destroyEx) {
                log.warn("新引擎初始化失败后清理也失败: type={}, id={}", engineType, newEngineId, destroyEx);
            }
            log.warn("新引擎初始化失败，回滚到旧引擎: type={}, id={}", engineType, newEngineId, initEx);
            throw initEx;
        }

        StorageEngine oldEngine = holder.delegate;
        holder.delegate = newEngine;
        if (engineType.isObjectStorage()) {
            objectProxy.delegate = newEngine;
        }
        if (defaultEngineType == engineType) {
            this.defaultEngineId = newEngineId;
        }

        try {
            provider.destroy(oldEngine);
        } catch (RuntimeException destroyEx) {
            log.warn("旧引擎销毁失败，但新引擎已生效: type={}, oldEngine={}, newEngine={}",
                    engineType, oldEngine.getClass().getSimpleName(),
                    newEngine.getClass().getSimpleName(), destroyEx);
        }

        log.info("刷新存储引擎完成: type={}, id={}, engine={}, actor={}, reason={}",
                engineType, newEngineId, newEngine.getClass().getSimpleName(),
                actor, reason);

        metrics.incrementSwitch(engineType);
        return newEngine;
    }

    private StorageEngineProvider findProvider(StorageEngineEnum engineType) {
        return getProviders().stream()
                .filter(p -> p.supportedEngineType() == engineType)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "未找到引擎类型 [" + engineType + "] 的 Provider，" +
                                "请确认对应引擎模块已引入 classpath"));
    }

    /**
     * 获取指定引擎类型的代理对象
     * <p>
     * 返回的代理始终有效，调用时懒解析当前引擎实例。
     * 业务代码通过 {@code @Autowired @Qualifier("xxxStorageEngine")} 注入此代理。
     *
     * @param engineType 引擎类型
     * @return JDK 动态代理对象
     */
    public StorageEngine getProxy(StorageEngineEnum engineType) {
        ProxyHolder holder = engineProxies.get(engineType);
        if (holder == null) {
            throw new IllegalStateException("未知的引擎类型: " + engineType);
        }
        return holder.proxy;
    }

    /**
     * 获取对象存储统一代理（所有对象存储引擎共享）
     * <p>
     * 对应 {@code @Qualifier("objectStorageEngine")} 注入点。
     */
    public StorageEngine getObjectProxy() {
        return objectProxy.proxy;
    }

    /**
     * 获取默认引擎的代理对象（@Primary 注入目标）
     */
    public StorageEngine getDefaultProxy() {
        if (defaultEngineType == null) {
            throw new IllegalStateException("尚未注册任何存储引擎");
        }
        return engineProxies.get(defaultEngineType).proxy;
    }

    /**
     * 获取指定类型的当前引擎实例（非代理，直接使用）
     *
     * @param engineType 引擎类型
     * @return 引擎实例，未注册时返回 null
     */
    public StorageEngine getEngine(StorageEngineEnum engineType) {
        ProxyHolder holder = engineProxies.get(engineType);
        return holder != null ? holder.delegate : null;
    }

    /**
     * 获取默认引擎实例
     */
    public StorageEngine getDefaultEngine() {
        if (defaultEngineType == null) return null;
        return engineProxies.get(defaultEngineType).delegate;
    }

    /**
     * 检查引擎是否已初始化
     */
    public boolean isInitialized() {
        return defaultEngineType != null;
    }

    /**
     * 获取当前默认引擎类型描述
     */
    public String getCurrentEngineType() {
        return defaultEngineType != null ? defaultEngineType.name() : "null";
    }

    /**
     * 获取所有已注册引擎的类型列表
     */
    public Set<StorageEngineEnum> getRegisteredTypes() {
        Set<StorageEngineEnum> result = new LinkedHashSet<>();
        engineProxies.forEach((type, holder) -> {
            if (holder.delegate != null) {
                result.add(type);
            }
        });
        return Collections.unmodifiableSet(result);
    }

    /**
     * 获取所有已注册引擎的快照（类型 → 实例）
     * <p>
     * 用于 HealthIndicator 和外部监控读取当前状态。返回的快照为不可变 Map，
     * 调用方不应假设顺序。
     */
    public Map<StorageEngineEnum, StorageEngine> snapshot() {
        Map<StorageEngineEnum, StorageEngine> result = new LinkedHashMap<>();
        engineProxies.forEach((type, holder) -> {
            if (holder.delegate != null) {
                result.put(type, holder.delegate);
            }
        });
        return Collections.unmodifiableMap(result);
    }

    // ========== 内部类 ==========

    /**
     * 引擎代理持有者
     * <p>
     * 每种引擎类型持有一个 volatile delegate 和一个 JDK 动态代理。
     * 代理对象在构造时创建，delegate 可运行时热替换。
     * 所有方法调用委托给当前的 delegate，未设置时抛出明确异常。
     */
    static class ProxyHolder {
        final StorageEngineEnum engineType;
        final StorageEngine proxy;
        volatile StorageEngine delegate;

        ProxyHolder(StorageEngineEnum engineType) {
            this.engineType = engineType;
            this.proxy = (StorageEngine) Proxy.newProxyInstance(
                    StorageEngine.class.getClassLoader(),
                    new Class<?>[]{StorageEngine.class},
                    StorageEngineInvocationHandler.forType(engineType, () -> this.delegate)
            );
        }

        @Override
        public String toString() {
            return "ProxyHolder{type=" + engineType +
                    ", delegate=" + (delegate != null ? delegate.getClass().getSimpleName() : "null") + "}";
        }
    }
}