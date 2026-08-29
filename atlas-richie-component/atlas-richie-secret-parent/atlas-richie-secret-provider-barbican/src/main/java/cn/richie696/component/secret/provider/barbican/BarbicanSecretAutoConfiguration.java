package cn.richie696.component.secret.provider.barbican;
import cn.richie696.component.secret.bootstrap.*; import cn.richie696.component.secret.starter.SecretRuntimeAutoConfiguration; import org.springframework.boot.autoconfigure.*; import org.springframework.boot.autoconfigure.condition.*; import org.springframework.context.annotation.Bean;
@AutoConfiguration @AutoConfigureBefore(SecretRuntimeAutoConfiguration.class) @ConditionalOnProperty(prefix = BootstrapSecretProperties.PREFIX, name = "enabled", havingValue = "true")
public class BarbicanSecretAutoConfiguration {
    @Bean(destroyMethod = "close") @ConditionalOnBean(SecretBootstrapState.class) @ConditionalOnMissingBean(BarbicanSecretClient.class)
    BarbicanSecretClient barbicanSecretClient(SecretBootstrapState state) { return state.topology().clients().values().stream().filter(BarbicanSecretClient.class::isInstance).map(BarbicanSecretClient.class::cast).findFirst().orElse(null); }
}
