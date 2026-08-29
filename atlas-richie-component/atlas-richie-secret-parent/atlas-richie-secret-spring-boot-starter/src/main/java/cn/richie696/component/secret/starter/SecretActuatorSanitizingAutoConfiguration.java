/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.starter;

import cn.richie696.component.secret.bootstrap.catalog.SecretBindingCatalogLoader;
import cn.richie696.component.secret.bootstrap.catalog.SecretBindingCatalogSet;
import cn.richie696.component.secret.bootstrap.SecretBootstrapState;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.endpoint.SanitizingFunction;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 强制隐藏 Binding Catalog 中声明的全部机密属性。
 */
@AutoConfiguration(after = SecretRuntimeAutoConfiguration.class)
@ConditionalOnClass(SanitizingFunction.class)
@ConditionalOnProperty(
        prefix = "platform.component.secret",
        name = "enabled",
        havingValue = "true")
public class SecretActuatorSanitizingAutoConfiguration {

    @Bean("atlasSecretSanitizingFunction")
    @ConditionalOnMissingBean(name = "atlasSecretSanitizingFunction")
    public SanitizingFunction atlasSecretSanitizingFunction(
            ObjectProvider<SecretBootstrapState> bootstrapStateProvider) {
        SecretBootstrapState state = bootstrapStateProvider.getIfAvailable();
        SecretBindingCatalogSet catalog = state == null ? null : state.catalogs();
        if (catalog == null) {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            ClassLoader classLoader = contextClassLoader == null
                    ? getClass().getClassLoader()
                    : contextClassLoader;
            catalog = new SecretBindingCatalogLoader().load(classLoader);
        }
        SecretBindingCatalogSet resolvedCatalog = catalog;
        return SanitizingFunction.sanitizeValue()
                .ifKeyMatches(key -> resolvedCatalog.propertySourceBinding(key).isPresent());
    }
}
