package cn.richie696.component.secret.provider.common;

import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.SecretBootstrapState;
import cn.richie696.component.secret.starter.SecretRuntimeAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/** Hands the bootstrap-created provider session to the runtime core. */
@AutoConfiguration
@AutoConfigureBefore(SecretRuntimeAutoConfiguration.class)
@ConditionalOnClass(RemoteSecretProviderClient.class)
@ConditionalOnProperty(prefix = BootstrapSecretProperties.PREFIX, name = "enabled", havingValue = "true")
public class RemoteProviderAutoConfiguration {
    @Bean(destroyMethod = "close")
    @ConditionalOnBean(SecretBootstrapState.class)
    @ConditionalOnMissingBean(RemoteSecretProviderClient.class)
    public RemoteSecretProviderClient remoteSecretProviderClient(
            ObjectProvider<SecretBootstrapState> stateProvider) {
        SecretBootstrapState state = stateProvider.getIfAvailable();
        if (state == null || !(state.client() instanceof RemoteSecretProviderClient client)) {
            throw new IllegalStateException("Secret provider is enabled but no selected remote provider was bootstrapped");
        }
        return client;
    }
}
