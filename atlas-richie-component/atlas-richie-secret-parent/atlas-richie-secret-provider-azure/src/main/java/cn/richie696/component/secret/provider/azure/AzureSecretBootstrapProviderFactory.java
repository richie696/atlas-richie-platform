package cn.richie696.component.secret.provider.azure;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapClient;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapProviderFactory;
import cn.richie696.component.secret.provider.common.RemoteProviderModuleSupport;
import cn.richie696.component.secret.provider.common.RemoteProviderProperties;
import cn.richie696.component.secret.provider.common.RemoteSecretProviderClient;
import java.util.Set;

@cn.richie696.component.secret.bootstrap.spi.SecretProviderType("azure")
public final class AzureSecretBootstrapProviderFactory implements SecretBootstrapProviderFactory {
    private static final Set<SecretCapability> CAPABILITIES = Set.of(
            SecretCapability.SECRET_READ, SecretCapability.SECRET_VERSIONING,
            SecretCapability.KEY_WRAP, SecretCapability.KEY_UNWRAP);

    @Override public String providerType() { return "azure"; }
    @Override public Set<SecretCapability> capabilities() { return CAPABILITIES; }

    @Override
    public SecretBootstrapClient create(BootstrapSecretProperties properties, SecretBootstrapContext context) {
        RemoteProviderProperties provider = RemoteProviderModuleSupport.bind(
                context.environment(), properties, "platform.component.secret.azure",
                RemoteProviderProperties.class, context);
        normalizeEmptyBearerToken(provider);
        RemoteProviderModuleSupport.validateSdk("azure", provider, CAPABILITIES,
                Set.of(RemoteProviderProperties.AuthenticationType.NONE));
        String providerId = context.providerId() == null || context.providerId().isBlank()
                ? providerType() : context.providerId();
        return new RemoteSecretProviderClient("azure", providerId,
                RemoteProviderModuleSupport.hash(providerId, provider), provider, properties, CAPABILITIES,
                new AzureSdkSecretTransport(provider));
    }

    private static void normalizeEmptyBearerToken(RemoteProviderProperties provider) {
        if (provider.getAuthentication().getType() == RemoteProviderProperties.AuthenticationType.BEARER_TOKEN
                && provider.getAuthentication().getToken().length == 0) {
            provider.getAuthentication().setType(RemoteProviderProperties.AuthenticationType.NONE);
        }
    }
}
