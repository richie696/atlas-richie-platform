/* Copyright (c) 2026 Richie (https://www.github.com/richie696) */
package cn.richie696.component.secret.provider.aliyun;

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

/** 阿里云启动客户端到运行期的生命周期移交。 */
@AutoConfiguration
@AutoConfigureBefore(SecretRuntimeAutoConfiguration.class)
@ConditionalOnClass(com.aliyun.kms20160120.Client.class)
@ConditionalOnProperty(prefix = BootstrapSecretProperties.PREFIX, name = "enabled", havingValue = "true")
public class AliyunSecretAutoConfiguration {
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(AliyunSecretClient.class)
    public AliyunSecretClient aliyunSecretClient(
            ConfigurableEnvironment environment,
            ObjectProvider<SecretBootstrapState> bootstrapStateProvider) {
        BootstrapSecretProperties bootstrap = Binder.get(environment)
                .bind(BootstrapSecretProperties.PREFIX, BootstrapSecretProperties.class)
                .orElseGet(BootstrapSecretProperties::new);
        var resolved = new AliyunSecretConfigurationResolver().resolve(environment, bootstrap);
        SecretBootstrapState state = bootstrapStateProvider.getIfAvailable();
        if (state != null && state.client() instanceof AliyunSecretClient bootstrapClient) {
            if (!resolved.configurationHash().equals(bootstrapClient.configurationHash())) {
                throw new IllegalStateException("Alibaba Cloud Secret Provider configuration changed after bootstrap");
            }
            return bootstrapClient;
        }
        return new AliyunClientFactory().create(resolved, bootstrap);
    }
}
