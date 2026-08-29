/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.provider.vault;

import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.SecretBootstrapState;
import cn.richie696.component.secret.starter.SecretRuntimeAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.vault.core.VaultTemplate;

/**
 * 将启动期 Vault 客户端安全移交给运行期。全局关闭时不会创建任何 Bean。
 */
@AutoConfiguration
@AutoConfigureBefore(SecretRuntimeAutoConfiguration.class)
@ConditionalOnClass(VaultTemplate.class)
@ConditionalOnProperty(
        prefix = BootstrapSecretProperties.PREFIX,
        name = "enabled",
        havingValue = "true")
public class VaultSecretAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(VaultSecretClient.class)
    public VaultSecretClient vaultSecretClient(
            ConfigurableEnvironment environment,
            ObjectProvider<SecretBootstrapState> bootstrapStateProvider) {
        SecretBootstrapState state = bootstrapStateProvider.getIfAvailable();
        if (state != null) {
            java.util.List<VaultSecretClient> clients = state.topology().clients().values().stream()
                    .filter(VaultSecretClient.class::isInstance)
                    .map(VaultSecretClient.class::cast)
                    .toList();
            return clients.size() == 1 ? clients.getFirst() : null;
        }
        BootstrapSecretProperties bootstrapProperties = Binder.get(environment)
                .bind(BootstrapSecretProperties.PREFIX, BootstrapSecretProperties.class)
                .orElseGet(BootstrapSecretProperties::new);
        VaultSecretConfigurationResolver.ResolvedVaultConfiguration resolved =
                new VaultSecretConfigurationResolver().resolve(environment, bootstrapProperties);
        return new VaultClientFactory().create(resolved, bootstrapProperties);
    }
}
