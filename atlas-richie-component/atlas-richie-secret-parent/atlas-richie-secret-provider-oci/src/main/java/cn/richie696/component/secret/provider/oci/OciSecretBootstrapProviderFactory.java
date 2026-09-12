package cn.richie696.component.secret.provider.oci;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapClient;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapProviderFactory;
import cn.richie696.component.secret.provider.common.RemoteProviderModuleSupport;
import cn.richie696.component.secret.provider.common.RemoteProviderProperties;
import cn.richie696.component.secret.provider.common.RemoteSecretProviderClient;
import java.util.Set;

@cn.richie696.component.secret.bootstrap.spi.SecretProviderType("oci")
public final class OciSecretBootstrapProviderFactory implements SecretBootstrapProviderFactory {
    private static final Set<SecretCapability> CAPABILITIES = Set.of(SecretCapability.SECRET_READ,
            SecretCapability.SECRET_VERSIONING, SecretCapability.KEY_WRAP, SecretCapability.KEY_UNWRAP);
    @Override public String providerType() { return "oci"; }
    @Override public Set<SecretCapability> capabilities() { return CAPABILITIES; }
    @Override public SecretBootstrapClient create(BootstrapSecretProperties properties, SecretBootstrapContext context) {
        RemoteProviderProperties provider = RemoteProviderModuleSupport.bind(context.environment(), properties,
                "platform.component.secret.oci", RemoteProviderProperties.class, context);
        RemoteProviderModuleSupport.validateSdk("oci", provider, CAPABILITIES,
                Set.of(RemoteProviderProperties.AuthenticationType.NONE,
                        RemoteProviderProperties.AuthenticationType.WORKLOAD_IDENTITY_TOKEN_FILE));
        String providerId = context.providerId() == null || context.providerId().isBlank() ? providerType() : context.providerId();
        return new RemoteSecretProviderClient("oci", providerId, RemoteProviderModuleSupport.hash(providerId, provider),
                provider, properties, CAPABILITIES, new OciSdkSecretTransport(provider));
    }
}
