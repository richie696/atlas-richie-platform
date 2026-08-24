package cn.richie696.component.secret.provider.oci;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.provider.common.AbstractRemoteProviderFactory;
import java.util.Set;

@cn.richie696.component.secret.bootstrap.spi.SecretProviderType("oci")
public final class OciSecretBootstrapProviderFactory extends AbstractRemoteProviderFactory {
    @Override protected String type() { return "oci"; }
    @Override protected String prefix() { return "platform.component.secret.oci"; }
    @Override protected Set<SecretCapability> providerCapabilities() { return Set.of(SecretCapability.SECRET_READ, SecretCapability.SECRET_VERSIONING, SecretCapability.KEY_WRAP, SecretCapability.KEY_UNWRAP); }
}
