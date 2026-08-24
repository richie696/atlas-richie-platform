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
                context.environment(), properties, prefix(), RemoteProviderProperties.class);
        OfficialWireProfiles.apply(type(), provider);
        RemoteProviderModuleSupport.validate(type(), provider);
        return RemoteProviderModuleSupport.client(
                type(), type(), RemoteProviderModuleSupport.hash(type(), provider), provider, properties,
                providerCapabilities());
    }
}
