/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.mfa.core.crypto;

import cn.richie696.component.secret.bootstrap.catalog.SecretBindingCatalogLoader;
import cn.richie696.component.secret.bootstrap.catalog.SecretExposure;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MfaSecretBindingCatalogTest {

    @Test
    void declaresTotpKeyAsNativeAndNeverImportsProviderCredentials() {
        var catalog = new SecretBindingCatalogLoader().load(getClass().getClassLoader());

        assertThat(catalog.bindings()).anySatisfy(binding -> {
            assertThat(binding.logicalName()).isEqualTo("mfa.totp.data-key");
            assertThat(binding.exposure()).isEqualTo(SecretExposure.NATIVE_HANDLE);
        });
        assertThat(catalog.propertySourceBinding(
                "platform.component.mfa.security.key-management.local.secret-key")).isEmpty();
        assertThat(catalog.propertySourceBinding(
                "platform.component.mfa.security.key-management.kms.access-key-secret")).isEmpty();
    }
}
