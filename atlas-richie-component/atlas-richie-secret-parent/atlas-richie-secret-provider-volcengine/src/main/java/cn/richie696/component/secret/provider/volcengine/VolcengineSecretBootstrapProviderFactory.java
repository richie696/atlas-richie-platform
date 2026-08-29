package cn.richie696.component.secret.provider.volcengine;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.provider.common.AbstractRemoteProviderFactory;
import java.util.Set;

@cn.richie696.component.secret.bootstrap.spi.SecretProviderType("volcengine")
public final class VolcengineSecretBootstrapProviderFactory extends AbstractRemoteProviderFactory {
    @Override protected String type() { return "volcengine"; }
    @Override protected String prefix() { return "platform.component.secret.volcengine"; }
    @Override protected Set<SecretCapability> providerCapabilities() {
        return Set.of(SecretCapability.KEY_WRAP, SecretCapability.KEY_UNWRAP);
    }
}
