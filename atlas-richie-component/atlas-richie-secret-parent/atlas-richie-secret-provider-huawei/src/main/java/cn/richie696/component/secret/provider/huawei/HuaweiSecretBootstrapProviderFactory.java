package cn.richie696.component.secret.provider.huawei;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapClient;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapProviderFactory;
import cn.richie696.component.secret.provider.common.RemoteProviderModuleSupport;
import cn.richie696.component.secret.provider.common.RemoteProviderProperties;
import cn.richie696.component.secret.provider.common.RemoteSecretProviderClient;
import java.util.Set;

@cn.richie696.component.secret.bootstrap.spi.SecretProviderType("huawei")
public final class HuaweiSecretBootstrapProviderFactory implements SecretBootstrapProviderFactory {
    private static final Set<SecretCapability> CAPABILITIES = Set.of(
            SecretCapability.SECRET_READ, SecretCapability.SECRET_VERSIONING,
            SecretCapability.KEY_WRAP, SecretCapability.KEY_UNWRAP);

    @Override public String providerType() { return "huawei"; }
    @Override public Set<SecretCapability> capabilities() { return CAPABILITIES; }

    @Override
    public SecretBootstrapClient create(BootstrapSecretProperties properties, SecretBootstrapContext context) {
        RemoteProviderProperties provider = RemoteProviderModuleSupport.bind(
                context.environment(), properties, "platform.component.secret.huawei",
                RemoteProviderProperties.class, context);
        RemoteProviderModuleSupport.validateSdk("huawei", provider, CAPABILITIES,
                Set.of(RemoteProviderProperties.AuthenticationType.ACCESS_KEY));
        String providerId = context.providerId() == null || context.providerId().isBlank()
                ? providerType() : context.providerId();
        return new RemoteSecretProviderClient("huawei", providerId,
                RemoteProviderModuleSupport.hash(providerId, provider), provider, properties, CAPABILITIES,
                new HuaweiSdkSecretTransport(provider));
    }
}
