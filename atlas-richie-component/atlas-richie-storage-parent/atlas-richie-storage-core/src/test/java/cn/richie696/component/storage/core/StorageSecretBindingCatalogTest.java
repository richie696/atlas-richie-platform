/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.storage.core;

import cn.richie696.component.secret.bootstrap.catalog.SecretBindingCatalogLoader;
import cn.richie696.component.secret.testkit.SecretBootstrapTestHarness;
import cn.richie696.component.storage.config.StorageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StorageSecretBindingCatalogTest {

    @Test
    void declaresOnlyKnownStorageCredentialProperties() {
        var catalog = new SecretBindingCatalogLoader().load(getClass().getClassLoader());

        assertThat(catalog.propertySourceBinding(
                "platform.component.storage.object.access-key-secret")).isPresent();
        assertThat(catalog.propertySourceBinding(
                "platform.component.storage.sftp.identity-file")).isEmpty();
    }

    @Test
    void enabledOverridesAndDisabledPreservesTheExistingPropertyPath() {
        Map<String, Object> remote = Map.of(
                "platform.component.storage.object.access-key-secret", "remote-storage-secret");

        var enabled = SecretBootstrapTestHarness.apply(getClass().getClassLoader(), Map.of(
                "spring.application.name", "storage-test",
                "platform.component.secret.enabled", true,
                "platform.component.secret.strict-mode", true,
                "platform.component.storage.object.access-key-secret", "local-storage-secret"), remote);
        var disabled = SecretBootstrapTestHarness.apply(getClass().getClassLoader(), Map.of(
                "spring.application.name", "storage-test",
                "platform.component.secret.enabled", false,
                "platform.component.storage.object.access-key-secret", "local-storage-secret"), remote);

        assertThat(Binder.get(enabled).bind("platform.component.storage", StorageProperties.class)
                .get().getObject().getAccessKeySecret()).isEqualTo("remote-storage-secret");
        assertThat(Binder.get(disabled).bind("platform.component.storage", StorageProperties.class)
                .get().getObject().getAccessKeySecret()).isEqualTo("local-storage-secret");
    }
}
