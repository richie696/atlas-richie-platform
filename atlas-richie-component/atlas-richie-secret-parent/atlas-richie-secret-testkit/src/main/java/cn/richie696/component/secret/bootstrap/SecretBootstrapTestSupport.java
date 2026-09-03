/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.bootstrap;

import cn.richie696.component.secret.bootstrap.catalog.SecretBindingCatalogLoader;
import org.springframework.boot.bootstrap.ConfigurableBootstrapContext;

/**
 * 仅供 atlas-richie-secret-testkit 使用的同包桥接类。
 *
 * <p>{@link AtlasSecretEnvironmentPostProcessor} 的三参构造器必须保持包可见：
 * Spring Boot 4.1 通过 spring.factories 反射实例化 EnvironmentPostProcessor 时，
 * 要求该类只能暴露唯一一个公共构造器（一参、由容器解析 ConfigurableBootstrapContext）。
 * 若把三参构造器改为 public，会破坏工厂加载契约。
 *
 * <p>本类与目标类同包（拆包只存在于 testkit 构件内），借同包可见性把
 * "自定义 Discovery / CatalogLoader 注入" 这一测试缝隙收口到唯一入口，
 * 避免业务组件测试夹具跨包直接 new 三参构造器。
 */
public final class SecretBootstrapTestSupport {

    private SecretBootstrapTestSupport() {
    }

    /**
     * 以受控协作对象构建 EnvironmentPostProcessor，等价于包内三参构造器。
     */
    public static AtlasSecretEnvironmentPostProcessor postProcessor(
            ConfigurableBootstrapContext bootstrapContext,
            SecretProviderDiscovery providerDiscovery,
            SecretBindingCatalogLoader catalogLoader) {
        return new AtlasSecretEnvironmentPostProcessor(bootstrapContext, providerDiscovery, catalogLoader);
    }
}
