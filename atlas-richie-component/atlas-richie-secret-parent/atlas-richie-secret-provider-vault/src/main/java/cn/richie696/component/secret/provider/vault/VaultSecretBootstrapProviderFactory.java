/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.provider.vault;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapClient;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapProviderFactory;
import cn.richie696.component.secret.bootstrap.spi.SecretProviderType;

import java.util.Set;

/**
 * 在 Spring Bean 创建前发现并创建 Vault Provider。
 */
@SecretProviderType("vault")
public final class VaultSecretBootstrapProviderFactory implements SecretBootstrapProviderFactory {
    private static final Set<SecretCapability> CAPABILITIES = Set.of(
            SecretCapability.SECRET_READ,
            SecretCapability.SECRET_VERSIONING,
            SecretCapability.KEY_WRAP,
            SecretCapability.KEY_UNWRAP,
            SecretCapability.SIGN,
            SecretCapability.VERIFY);

    @Override
    public String providerType() {
        return "vault";
    }

    @Override
    public Set<SecretCapability> capabilities() {
        return CAPABILITIES;
    }

    @Override
    public SecretBootstrapClient create(
            BootstrapSecretProperties properties,
            SecretBootstrapContext context) {
        VaultSecretConfigurationResolver.ResolvedVaultConfiguration resolved =
                new VaultSecretConfigurationResolver().resolve(context.environment(), properties, context);
        return new VaultClientFactory().create(resolved, properties);
    }
}
