/* Copyright (c) 2026 Richie (https://www.github.com/richie696) */
package cn.richie696.component.secret.provider.aliyun;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapClient;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapProviderFactory;
import cn.richie696.component.secret.bootstrap.spi.SecretProviderType;

import java.util.Set;

/** 阿里云启动期 Provider 工厂。 */
@SecretProviderType("aliyun")
public final class AliyunSecretBootstrapProviderFactory implements SecretBootstrapProviderFactory {
    private static final Set<SecretCapability> CAPABILITIES = Set.of(
            SecretCapability.SECRET_READ, SecretCapability.SECRET_VERSIONING,
            SecretCapability.KEY_WRAP, SecretCapability.KEY_UNWRAP);
    @Override public String providerType() { return "aliyun"; }
    @Override public Set<SecretCapability> capabilities() { return CAPABILITIES; }
    @Override public SecretBootstrapClient create(
            BootstrapSecretProperties properties, SecretBootstrapContext context) {
        var resolved = new AliyunSecretConfigurationResolver().resolve(context.environment(), properties);
        return new AliyunClientFactory().create(resolved, properties);
    }
}
