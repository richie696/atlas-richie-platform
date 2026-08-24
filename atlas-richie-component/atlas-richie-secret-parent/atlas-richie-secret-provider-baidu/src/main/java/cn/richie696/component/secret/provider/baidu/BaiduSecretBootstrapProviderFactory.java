package cn.richie696.component.secret.provider.baidu;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.provider.common.AbstractRemoteProviderFactory;
import java.util.Set;

@cn.richie696.component.secret.bootstrap.spi.SecretProviderType("baidu")
public final class BaiduSecretBootstrapProviderFactory extends AbstractRemoteProviderFactory {
    @Override protected String type() { return "baidu"; }
    @Override protected String prefix() { return "platform.component.secret.baidu"; }
    @Override protected Set<SecretCapability> providerCapabilities() { return Set.of(SecretCapability.KEY_WRAP, SecretCapability.KEY_UNWRAP); }
}
