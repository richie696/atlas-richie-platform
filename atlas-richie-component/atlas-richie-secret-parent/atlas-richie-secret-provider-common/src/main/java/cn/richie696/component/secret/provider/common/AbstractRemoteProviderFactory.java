package cn.richie696.component.secret.provider.common;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapClient;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapProviderFactory;

import java.util.Set;

/** Common factory contract for optional M2/M3 provider artifacts. */
public abstract class AbstractRemoteProviderFactory implements SecretBootstrapProviderFactory {
    protected abstract String type();
    protected abstract String prefix();
    protected abstract Set<SecretCapability> providerCapabilities();

    @Override public final String providerType() { return type(); }
    @Override public final Set<SecretCapability> capabilities() { return providerCapabilities(); }

    @Override
    public final SecretBootstrapClient create(BootstrapSecretProperties properties, SecretBootstrapContext context) {
        RemoteProviderProperties provider = RemoteProviderModuleSupport.bind(
                context.environment(), properties, prefix(), RemoteProviderProperties.class, context);
        OfficialWireProfiles.apply(type(), provider);
        RemoteProviderModuleSupport.validate(type(), provider, providerCapabilities());
        String providerId = context.providerId() == null || context.providerId().isBlank()
                ? type() : context.providerId();
        return RemoteProviderModuleSupport.client(
                type(), providerId, RemoteProviderModuleSupport.hash(providerId, provider), provider, properties,
                providerCapabilities());
    }
}
