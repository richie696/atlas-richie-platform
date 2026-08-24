package cn.richie696.component.secret.provider.tencent;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.provider.common.AbstractRemoteProviderFactory;
import java.util.Set;

@cn.richie696.component.secret.bootstrap.spi.SecretProviderType("tencent")
public final class TencentSecretBootstrapProviderFactory extends AbstractRemoteProviderFactory {
    @Override protected String type() { return "tencent"; }
    @Override protected String prefix() { return "platform.component.secret.tencent"; }
    @Override protected Set<SecretCapability> providerCapabilities() { return Set.of(SecretCapability.SECRET_READ, SecretCapability.SECRET_VERSIONING, SecretCapability.KEY_WRAP, SecretCapability.KEY_UNWRAP); }
}
