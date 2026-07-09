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
package com.richie.component.storage.core;

import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.enums.StorageEngineEnum;
import com.richie.component.storage.observability.StorageHealthIndicator;
import com.richie.component.storage.observability.StorageMetricsBinder;
import com.richie.context.common.api.SpringContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.autoconfigure.contributor.ConditionalOnEnabledHealthIndicator;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 存储引擎自动配置
 * <p>
 * 注册 Proxy + Registry，并在自动模式下将 Spring Bean 绑定到 Proxy delegate。
 * 手动模式下注册 qualifier-named Bean，使 {@code @Qualifier} 注入在两种模式下一致。
 *
 * @author richie696
 * @version 1.0
 * @since 2024-01-01
 */
@Slf4j
@Configuration
public class StorageEngineAutoConfiguration {

    /**
     * JDK 动态代理 Bean（自动模式下作为 @Primary）
     * <p>
     * 标记为 @Primary，确保业务代码 {@code @Autowired StorageEngine} 时注入的是代理对象。
     * 手动模式下此 Bean 仍然存在，但不会被用作 @Primary（由 defaultStorageEngine 替代）。
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(StorageEngineProxyFactoryBean.class)
    public StorageEngineProxyFactoryBean storageEngineProxy() {
        return new StorageEngineProxyFactoryBean();
    }

    /**
     * Registry（始终创建，不受 auto-init 影响）
     * <p>
     * 内部为每种引擎类型预创建 ProxyHolder，支持多引擎并存。
     */
    @Bean
    @ConditionalOnMissingBean
    public StorageEngineRegistry storageEngineRegistry() {
        return new StorageEngineRegistry();
    }

    // ========== 可观测性：Actuator HealthIndicator + Micrometer 指标 ==========

    /**
     * 存储引擎健康指示器，仅在 Spring Boot Actuator 启动器存在时注册。
     * <p>
     * 遵循 Spring Boot 标准的 health 开关：
     * {@code management.health.storage.enabled=false}（或全局 {@code management.health.defaults.enabled=false}）可关闭。
     * 未对接 Spring Boot Actuator 或 APM 监控时关闭可避免噪音日志。
     */
    @Bean
    @ConditionalOnEnabledHealthIndicator("storage")
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    @ConditionalOnMissingBean
    public StorageHealthIndicator storageHealthIndicator(StorageEngineRegistry registry) {
        return new StorageHealthIndicator(registry);
    }

    /**
     * 存储引擎运行时指标绑定器（Micrometer）。
     * <p>
     * 仅在 Micrometer 的 {@code MeterBinder} 可用时注册，Spring Boot Actuator
     * 启动时会自动调用 {@code bindTo(MeterRegistry)}。
     * <p>
     * 遵循 Spring Boot 标准的 metrics 开关：
     * {@code management.metrics.enable.storage=NONE} 可关闭（默认 {@code ALL}）。
     * 未对接 Prometheus/Grafana 等监控系统时关闭可避免 CollectorRegistry 报错。
     */
    @Bean
    @ConditionalOnProperty(prefix = "management.metrics.enable", name = "storage",
            havingValue = "ALL", matchIfMissing = true)
    @ConditionalOnClass(name = "io.micrometer.core.instrument.binder.MeterBinder")
    @ConditionalOnMissingBean
    public StorageMetricsBinder storageMetricsBinder(StorageEngineRegistry registry) {
        return new StorageMetricsBinder(registry);
    }

    // ========== 手动模式：注册 qualifier-named Bean ==========

    /**
     * 手动模式：对象存储引擎代理
     * <p>
     * 所有对象存储（MinIO/OSS/COS/OBS/S3/KS3/TOS/Azure）共享此 Bean，
     * 对应 {@code @Qualifier("objectStorageEngine")} 注入点。
     */
    @Bean("objectStorageEngine")
    @ConditionalOnProperty(prefix = "platform.component.storage",
            name = "auto-init", havingValue = "false")
    public StorageEngine manualObjectStorageEngine(StorageEngineRegistry registry) {
        return registry.getObjectProxy();
    }

    @Bean("ftpStorageEngine")
    @ConditionalOnProperty(prefix = "platform.component.storage",
            name = "auto-init", havingValue = "false")
    public StorageEngine manualFtpStorageEngine(StorageEngineRegistry registry) {
        return registry.getProxy(StorageEngineEnum.FTP);
    }

    @Bean("sftpStorageEngine")
    @ConditionalOnProperty(prefix = "platform.component.storage",
            name = "auto-init", havingValue = "false")
    public StorageEngine manualSftpStorageEngine(StorageEngineRegistry registry) {
        return registry.getProxy(StorageEngineEnum.SFTP);
    }

    @Bean("smbStorageEngine")
    @ConditionalOnProperty(prefix = "platform.component.storage",
            name = "auto-init", havingValue = "false")
    public StorageEngine manualSmbStorageEngine(StorageEngineRegistry registry) {
        return registry.getProxy(StorageEngineEnum.SMB);
    }

    @Bean("localStorageEngine")
    @ConditionalOnProperty(prefix = "platform.component.storage",
            name = "auto-init", havingValue = "false")
    public StorageEngine manualLocalStorageEngine(StorageEngineRegistry registry) {
        return registry.getProxy(StorageEngineEnum.LOCAL);
    }

    /**
     * 手动模式：默认引擎代理（@Primary）
     * <p>
     * 业务代码 {@code @Autowired StorageEngine} 时注入此代理，
     * 代理委托到 Registry 中第一个注册的引擎。
     * <p>
     * 使用 @ConditionalOnMissingBean(StorageEngine.class) 避免与自动模式的
     * StorageEngineProxyFactoryBean 冲突。
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(StorageEngine.class)
    @ConditionalOnProperty(prefix = "platform.component.storage",
            name = "auto-init", havingValue = "false")
    public StorageEngine defaultStorageEngine(StorageEngineRegistry registry) {
        return registry.getDefaultProxy();
    }

    // ========== 自动模式：将 @Service Bean 绑定到 Registry + Proxy ==========

    /**
     * 自动模式下：将 Spring 管理的引擎 Bean 注册到 Registry 并绑定 Proxy
     * <p>
     * 自动模式下可以多个引擎共存（对象存储 + FTP + SFTP 等），
     * Registry 为每种类型维护独立的 Proxy。
     * 优先级最高的引擎作为默认引擎（object > ftp > sftp > smb > local）。
     */
    @Bean
    @ConditionalOnProperty(prefix = "platform.component.storage",
            name = "auto-init", havingValue = "true", matchIfMissing = true)
    public ApplicationRunner autoBindEngineToProxy(
            StorageEngineRegistry registry,
            StorageEngineProxyFactoryBean proxyFactory,
            @Autowired(required = false) @Qualifier("objectStorageEngine")
                    StorageEngine objectEngine,
            @Autowired(required = false) @Qualifier("ftpStorageEngine")
                    StorageEngine ftpEngine,
            @Autowired(required = false) @Qualifier("sftpStorageEngine")
                    StorageEngine sftpEngine,
            @Autowired(required = false) @Qualifier("smbStorageEngine")
                    StorageEngine smbEngine,
            @Autowired(required = false) @Qualifier("localStorageEngine")
                    StorageEngine localEngine,
            StorageProperties properties) {
        return args -> {
            // 按优先级选择默认引擎（object > ftp > sftp > smb > local）
            var defaultEntry = Stream.of(
                            new EngineEntry<>("object", objectEngine),
                            new EngineEntry<>("ftp", ftpEngine),
                            new EngineEntry<>("sftp", sftpEngine),
                            new EngineEntry<>("smb", smbEngine),
                            new EngineEntry<>("local", localEngine))
                    .filter(e -> e.engine != null)
                    .findFirst()
                    .orElse(null);

            if (defaultEntry == null) {
                log.warn("自动模式启动绑定：未找到可用的存储引擎 Bean，请检查 YAML 配置，actor=auto-init, reason=startup");
                return;
            }

            // 注册默认引擎到 Registry
            String defaultEngineId = resolveEngineId(defaultEntry.typeKey,
                    defaultEntry.engine, properties);
            StorageEngineEnum defaultEngineType = resolveEngineType(
                    defaultEntry.typeKey, defaultEntry.engine, properties);
            log.info("自动模式启动绑定：开始注册默认引擎: type={}, id={}, engine={}, actor=auto-init, reason=startup",
                    defaultEngineType, defaultEngineId,
                    defaultEntry.engine.getClass().getSimpleName());

            registry.registerInitialEngine(defaultEngineType, defaultEngineId,
                    defaultEntry.engine);

            // 绑定默认引擎到 @Primary Proxy
            proxyFactory.setDelegate(defaultEntry.engine);
            log.info("自动模式启动绑定：默认引擎已绑定到 Proxy: type={}, id={}, engine={}, actor=auto-init, reason=startup",
                    defaultEngineType, defaultEngineId,
                    defaultEntry.engine.getClass().getSimpleName());

            // 注册其他非默认引擎到 Registry
            registerIfPresent(registry, StorageEngineEnum.FTP, "ftp", ftpEngine, properties, "auto-init", "startup");
            registerIfPresent(registry, StorageEngineEnum.SFTP, "sftp", sftpEngine, properties, "auto-init", "startup");
            registerIfPresent(registry, StorageEngineEnum.SMB, "smb", smbEngine, properties, "auto-init", "startup");
            registerIfPresent(registry, StorageEngineEnum.LOCAL, "local", localEngine, properties, "auto-init", "startup");

            // 注册对象存储引擎（如果存在且不是默认引擎）
            if (objectEngine != null && defaultEntry.engine != objectEngine) {
                StorageEngineEnum objectType = resolveObjectEngineType(objectEngine, properties);
                if (objectType != null) {
                    String objectId = resolveEngineId(objectType.getConfigValue(),
                            objectEngine, properties);
                    log.info("自动模式启动绑定：注册非默认对象引擎: type={}, id={}, engine={}, actor=auto-init, reason=startup",
                            objectType, objectId,
                            objectEngine.getClass().getSimpleName());
                    registry.registerInitialEngine(objectType, objectId, objectEngine);
                }
            }
        };
    }

    private void registerIfPresent(StorageEngineRegistry registry,
                                    StorageEngineEnum engineType, String typeKey,
                                    StorageEngine engine, StorageProperties properties,
                                    String actor, String reason) {
        if (engine != null && !registry.getRegisteredTypes().contains(engineType)) {
            String engineId = resolveEngineId(typeKey, engine, properties);
            log.info("自动模式启动绑定：注册非默认引擎: type={}, id={}, engine={}, actor={}, reason={}",
                    engineType, engineId,
                    engine.getClass().getSimpleName(), actor, reason);
            registry.registerInitialEngine(engineType, engineId, engine);
        }
    }

    private StorageEngineEnum resolveEngineType(String typeKey, StorageEngine engine,
                                                 StorageProperties properties) {
        if ("object".equals(typeKey)) {
            return resolveObjectEngineType(engine, properties);
        }
        return StorageEngineEnum.fromConfigValue(typeKey).orElse(null);
    }

    private StorageEngineEnum resolveObjectEngineType(StorageEngine engine,
                                                       StorageProperties properties) {
        if (properties.getObject().getEngine() != null) {
            return properties.getObject().getEngine();
        }
        // Provider.supports() 优先于类名匹配，避免命名变更导致类型推断失效
        return resolveObjectEngineTypeByProvider(engine);
    }

    private StorageEngineEnum resolveObjectEngineTypeByProvider(StorageEngine engine) {
        ApplicationContext ctx = SpringContextHolder.getApplicationContext();
        if (ctx == null) {
            log.warn("Spring 上下文未就绪，无法通过 Provider 推断对象存储引擎类型");
            return null;
        }
        Map<String, StorageEngineProvider> providers = ctx.getBeansOfType(StorageEngineProvider.class);
        List<StorageEngineProvider> matched = providers.values().stream()
                .filter(p -> p.supports(engine.getClass()))
                .toList();
        if (matched.isEmpty()) {
            log.warn("未找到支持 {} 的 StorageEngineProvider，请检查类路径与 @Bean 注册",
                    engine.getClass().getName());
            return null;
        }
        if (matched.size() > 1) {
            log.warn("多个 Provider 同时声明支持 {}，将取第一个：{}",
                    engine.getClass().getName(),
                    matched.get(0).getClass().getName());
        }
        return matched.get(0).supportedEngineType();
    }

    private String resolveEngineId(String type, StorageEngine engine,
                                   StorageProperties properties) {
        if (properties.getObject().getEngine() != null
                && type.equals(properties.getObject().getEngine().getConfigValue())) {
            return "default-" + type;
        }
        return "default-" + engine.getClass().getSimpleName()
                .replace("StorageEngine", "").toLowerCase();
    }

    /**
     * 内部辅助类：引擎类型 + 实例配对
     */
    private record EngineEntry<T>(String typeKey, T engine) {}
}
