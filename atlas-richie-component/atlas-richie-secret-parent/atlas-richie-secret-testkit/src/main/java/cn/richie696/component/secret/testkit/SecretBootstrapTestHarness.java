/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.testkit;

import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.SecretBootstrapTestSupport;
import cn.richie696.component.secret.bootstrap.SecretProviderDiscovery;
import cn.richie696.component.secret.bootstrap.catalog.SecretBindingCatalogLoader;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapProviderFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;
import java.util.Map;

/** 运行真实 Catalog 过滤和 PropertySource 注入、但不访问网络的业务组件测试夹具。 */
public final class SecretBootstrapTestHarness {
    private SecretBootstrapTestHarness() {
    }

    public static ConfigurableEnvironment apply(
            ClassLoader classLoader,
            Map<String, Object> localProperties,
            Map<String, Object> remoteValues) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test-local", localProperties));
        final SecretBootstrapProviderFactory factory =
                new StaticSecretBootstrapProviderFactory("test", remoteValues);
        SecretProviderDiscovery discovery = new SecretProviderDiscovery() {
            @Override
            public List<SecretBootstrapProviderFactory> discover(
                    ClassLoader ignored,
                    BootstrapSecretProperties properties) {
                return List.of(factory);
            }
        };
        SpringApplication application = new SpringApplication();
        application.setResourceLoader(new DefaultResourceLoader(classLoader));
        SecretBootstrapTestSupport.postProcessor(
                new DefaultBootstrapContext(), discovery, new SecretBindingCatalogLoader())
                .postProcessEnvironment(environment, application);
        return environment;
    }
}
