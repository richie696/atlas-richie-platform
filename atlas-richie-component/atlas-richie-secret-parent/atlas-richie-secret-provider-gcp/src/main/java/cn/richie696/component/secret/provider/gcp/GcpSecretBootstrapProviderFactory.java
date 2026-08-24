package cn.richie696.component.secret.provider.gcp;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.provider.common.AbstractRemoteProviderFactory;
import java.util.Set;

@cn.richie696.component.secret.bootstrap.spi.SecretProviderType("gcp")
public final class GcpSecretBootstrapProviderFactory extends AbstractRemoteProviderFactory {
    @Override protected String type() { return "gcp"; }
    @Override protected String prefix() { return "platform.component.secret.gcp"; }
    @Override protected Set<SecretCapability> providerCapabilities() { return Set.of(SecretCapability.SECRET_READ, SecretCapability.SECRET_VERSIONING, SecretCapability.KEY_WRAP, SecretCapability.KEY_UNWRAP); }
}
