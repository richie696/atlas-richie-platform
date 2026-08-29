/* Copyright (c) 2026 Richie (https://www.github.com/richie696) */
package cn.richie696.component.secret.provider.aws;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapClient;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapProviderFactory;
import cn.richie696.component.secret.bootstrap.spi.SecretProviderType;

import java.util.Set;

/** AWS 启动期 Provider 工厂。 */
@SecretProviderType("aws")
public final class AwsSecretBootstrapProviderFactory implements SecretBootstrapProviderFactory {
    private static final Set<SecretCapability> CAPABILITIES = Set.of(
            SecretCapability.SECRET_READ, SecretCapability.SECRET_VERSIONING,
            SecretCapability.KEY_WRAP, SecretCapability.KEY_UNWRAP,
            SecretCapability.SIGN, SecretCapability.VERIFY);
    @Override public String providerType() { return "aws"; }
    @Override public Set<SecretCapability> capabilities() { return CAPABILITIES; }
    @Override public SecretBootstrapClient create(
            BootstrapSecretProperties properties, SecretBootstrapContext context) {
        var resolved = new AwsSecretConfigurationResolver().resolve(context.environment(), properties, context);
        return new AwsClientFactory().create(resolved, properties);
    }
}
