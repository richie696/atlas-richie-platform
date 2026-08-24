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
        String propertySourceName) {

    public SecretBootstrapState(
            BootstrapSecretProperties properties,
            SecretBootstrapProviderFactory factory,
            SecretBootstrapClient client,
            SecretBootstrapResult result) {
        this(properties, factory, client, result, null, null, null);
    }
}
