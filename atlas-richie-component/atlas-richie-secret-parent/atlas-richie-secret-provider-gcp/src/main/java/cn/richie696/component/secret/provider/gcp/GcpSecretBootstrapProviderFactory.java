package cn.richie696.component.secret.provider.gcp;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapClient;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapProviderFactory;
import cn.richie696.component.secret.provider.common.RemoteProviderModuleSupport;
import cn.richie696.component.secret.provider.common.RemoteProviderProperties;
import cn.richie696.component.secret.provider.common.RemoteSecretProviderClient;
import java.util.Set;

@cn.richie696.component.secret.bootstrap.spi.SecretProviderType("gcp")
public final class GcpSecretBootstrapProviderFactory implements SecretBootstrapProviderFactory {
    private static final Set<SecretCapability> CAPABILITIES = Set.of(
            SecretCapability.SECRET_READ, SecretCapability.SECRET_VERSIONING,
            SecretCapability.KEY_WRAP, SecretCapability.KEY_UNWRAP);
    @Override public String providerType() { return "gcp"; }
    @Override public Set<SecretCapability> capabilities() { return CAPABILITIES; }
    @Override public SecretBootstrapClient create(BootstrapSecretProperties properties, SecretBootstrapContext context) {
        RemoteProviderProperties provider = RemoteProviderModuleSupport.bind(
                context.environment(), properties, "platform.component.secret.gcp",
                RemoteProviderProperties.class, context);
        normalizeEmptyBearerToken(provider);
        RemoteProviderModuleSupport.validateSdk("gcp", provider, CAPABILITIES,
                Set.of(RemoteProviderProperties.AuthenticationType.NONE));
        String providerId = context.providerId() == null || context.providerId().isBlank()
                ? providerType() : context.providerId();
        return new RemoteSecretProviderClient("gcp", providerId,
                RemoteProviderModuleSupport.hash(providerId, provider), provider, properties, CAPABILITIES,
                new GcpSdkSecretTransport(provider));
    }

    private static void normalizeEmptyBearerToken(RemoteProviderProperties provider) {
        if (provider.getAuthentication().getType() == RemoteProviderProperties.AuthenticationType.BEARER_TOKEN
                && provider.getAuthentication().getToken().length == 0) {
            provider.getAuthentication().setType(RemoteProviderProperties.AuthenticationType.NONE);
        }
    }
}
