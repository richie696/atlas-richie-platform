package cn.richie696.component.secret.provider.ibm;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapClient;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapProviderFactory;
import cn.richie696.component.secret.provider.common.RemoteProviderModuleSupport;
import cn.richie696.component.secret.provider.common.RemoteProviderProperties;
import cn.richie696.component.secret.provider.common.RemoteSecretProviderClient;
import java.util.Set;

@cn.richie696.component.secret.bootstrap.spi.SecretProviderType("ibm-key-protect")
public final class IBMKeyProtectSecretBootstrapProviderFactory implements SecretBootstrapProviderFactory {
    private static final Set<SecretCapability> CAPABILITIES = Set.of(
            SecretCapability.KEY_WRAP, SecretCapability.KEY_UNWRAP);
    @Override public String providerType() { return "ibm-key-protect"; }
    @Override public Set<SecretCapability> capabilities() { return CAPABILITIES; }
    @Override public SecretBootstrapClient create(BootstrapSecretProperties properties, SecretBootstrapContext context) {
        RemoteProviderProperties provider = RemoteProviderModuleSupport.bind(
                context.environment(), properties, "platform.component.secret.ibm-key-protect",
                RemoteProviderProperties.class, context);
        RemoteProviderModuleSupport.validateSdk("ibm-key-protect", provider, CAPABILITIES,
                Set.of(RemoteProviderProperties.AuthenticationType.BEARER_TOKEN));
        String providerId = context.providerId() == null || context.providerId().isBlank() ? providerType() : context.providerId();
        return new RemoteSecretProviderClient("ibm-key-protect", providerId,
                RemoteProviderModuleSupport.hash(providerId, provider), provider, properties, CAPABILITIES,
                new IbmKeyProtectSdkSecretTransport(provider));
    }
}
