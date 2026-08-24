package cn.richie696.component.secret.provider.openbao;

import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.SecretBootstrapState;
import cn.richie696.component.secret.starter.SecretRuntimeAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureBefore(SecretRuntimeAutoConfiguration.class)
@ConditionalOnProperty(prefix = BootstrapSecretProperties.PREFIX, name = "enabled", havingValue = "true")
public class OpenBaoSecretAutoConfiguration {
    @Bean(destroyMethod = "close")
    @ConditionalOnBean(SecretBootstrapState.class)
    @ConditionalOnMissingBean(OpenBaoSecretClient.class)
    OpenBaoSecretClient openBaoSecretClient(SecretBootstrapState state) {
        if (!(state.client() instanceof OpenBaoSecretClient client)) throw new IllegalStateException("OpenBao bootstrap client is not available");
        return client;
    }
}
