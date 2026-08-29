package cn.richie696.component.secret.provider.openbao;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.*;
import java.util.Set;

@SecretProviderType("openbao")
public final class OpenBaoSecretBootstrapProviderFactory implements SecretBootstrapProviderFactory {
    private static final Set<SecretCapability> CAPABILITIES = Set.of(SecretCapability.SECRET_READ, SecretCapability.SECRET_VERSIONING, SecretCapability.KEY_WRAP, SecretCapability.KEY_UNWRAP, SecretCapability.SIGN, SecretCapability.VERIFY);
    @Override public String providerType() { return "openbao"; }
    @Override public Set<SecretCapability> capabilities() { return CAPABILITIES; }
    @Override public SecretBootstrapClient create(BootstrapSecretProperties properties, SecretBootstrapContext context) { return OpenBaoSecretClient.create(context.environment(), properties, context); }
}
