/* Copyright (c) 2026 Richie (https://www.github.com/richie696) */
package cn.richie696.component.secret.provider.aws;

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
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

/** AWS 启动客户端到运行期的生命周期移交。 */
@AutoConfiguration
@AutoConfigureBefore(SecretRuntimeAutoConfiguration.class)
@ConditionalOnClass(SecretsManagerClient.class)
@ConditionalOnProperty(prefix = BootstrapSecretProperties.PREFIX, name = "enabled", havingValue = "true")
public class AwsSecretAutoConfiguration {
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(AwsSecretClient.class)
    public AwsSecretClient awsSecretClient(
            ConfigurableEnvironment environment,
            ObjectProvider<SecretBootstrapState> bootstrapStateProvider) {
        BootstrapSecretProperties bootstrap = Binder.get(environment)
                .bind(BootstrapSecretProperties.PREFIX, BootstrapSecretProperties.class)
                .orElseGet(BootstrapSecretProperties::new);
        var resolved = new AwsSecretConfigurationResolver().resolve(environment, bootstrap);
        SecretBootstrapState state = bootstrapStateProvider.getIfAvailable();
        if (state != null && state.client() instanceof AwsSecretClient bootstrapClient) {
            if (!resolved.configurationHash().equals(bootstrapClient.configurationHash())) {
                throw new IllegalStateException("AWS Secret Provider configuration changed after bootstrap");
            }
            return bootstrapClient;
        }
        return new AwsClientFactory().create(resolved, bootstrap);
    }
}
