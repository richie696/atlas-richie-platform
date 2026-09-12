package cn.richie696.component.secret.provider.volcengine;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapClient;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapProviderFactory;
import cn.richie696.component.secret.provider.common.RemoteProviderModuleSupport;
import cn.richie696.component.secret.provider.common.RemoteProviderProperties;
import cn.richie696.component.secret.provider.common.RemoteSecretProviderClient;
import java.util.Set;

@cn.richie696.component.secret.bootstrap.spi.SecretProviderType("volcengine")
public final class VolcengineSecretBootstrapProviderFactory implements SecretBootstrapProviderFactory {
    private static final Set<SecretCapability> CAPABILITIES = Set.of(
            SecretCapability.KEY_WRAP, SecretCapability.KEY_UNWRAP);

    @Override public String providerType() { return "volcengine"; }
    @Override public Set<SecretCapability> capabilities() { return CAPABILITIES; }

    @Override
    public SecretBootstrapClient create(BootstrapSecretProperties properties, SecretBootstrapContext context) {
        RemoteProviderProperties provider = RemoteProviderModuleSupport.bind(
                context.environment(), properties, "platform.component.secret.volcengine",
                RemoteProviderProperties.class, context);
        RemoteProviderModuleSupport.validateSdk("volcengine", provider, CAPABILITIES,
                Set.of(RemoteProviderProperties.AuthenticationType.ACCESS_KEY));
        String providerId = context.providerId() == null || context.providerId().isBlank()
                ? providerType() : context.providerId();
        return new RemoteSecretProviderClient("volcengine", providerId,
                RemoteProviderModuleSupport.hash(providerId, provider), provider, properties, CAPABILITIES,
                new VolcengineSdkSecretTransport(provider));
    }
}
