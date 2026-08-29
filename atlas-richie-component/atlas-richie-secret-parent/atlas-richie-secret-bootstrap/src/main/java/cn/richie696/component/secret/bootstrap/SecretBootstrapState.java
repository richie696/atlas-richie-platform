/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.bootstrap;

import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapClient;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapProviderFactory;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapRequest;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapResult;
import cn.richie696.component.secret.bootstrap.catalog.SecretBindingCatalogSet;

/**
 * 从启动期向运行期移交的不可变状态。
 */
public record SecretBootstrapState(
        BootstrapSecretProperties properties,
        SecretBootstrapProviderFactory factory,
        SecretBootstrapClient client,
        SecretBootstrapResult result,
        SecretBootstrapRequest request,
        SecretBindingCatalogSet catalogs,
        String propertySourceName,
        SecretProviderTopology topology) {

    public SecretBootstrapState {
        if (topology == null && factory != null && client != null) {
            String providerId = result == null || result.providerId() == null
                    ? factory.providerType() : result.providerId();
            topology = new SecretProviderTopology(
                    java.util.Map.of(providerId, factory),
                    java.util.Map.of(providerId, client),
                    java.util.Map.of(),
                    providerId);
        }
    }

    public SecretBootstrapState(
            BootstrapSecretProperties properties,
            SecretBootstrapProviderFactory factory,
            SecretBootstrapClient client,
            SecretBootstrapResult result,
            SecretBootstrapRequest request,
            SecretBindingCatalogSet catalogs,
            String propertySourceName) {
        this(properties, factory, client, result, request, catalogs, propertySourceName, null);
    }

    public SecretBootstrapState(
            BootstrapSecretProperties properties,
            SecretBootstrapProviderFactory factory,
            SecretBootstrapClient client,
            SecretBootstrapResult result) {
        this(properties, factory, client, result, null, null, null, null);
    }

    @Override
    public String toString() {
        return "SecretBootstrapState[properties=[PROTECTED], factory="
                + (factory == null ? null : factory.providerType())
                + ", client=[PROTECTED], result=[PROTECTED], request=[PROTECTED], catalogs="
                + (catalogs == null ? null : catalogs.bindings().size())
                + ", propertySourceName=" + propertySourceName + ", topology=[PROTECTED]]";
    }
}
