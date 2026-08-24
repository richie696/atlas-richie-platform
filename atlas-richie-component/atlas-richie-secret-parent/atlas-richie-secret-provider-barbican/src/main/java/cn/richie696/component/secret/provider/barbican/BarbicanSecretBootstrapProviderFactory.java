package cn.richie696.component.secret.provider.barbican;
import cn.richie696.component.secret.api.SecretCapability; import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties; import cn.richie696.component.secret.bootstrap.spi.*; import java.util.Set;
@cn.richie696.component.secret.bootstrap.spi.SecretProviderType("barbican")
public final class BarbicanSecretBootstrapProviderFactory implements SecretBootstrapProviderFactory {
    private static final Set<SecretCapability> CAPABILITIES = Set.of(SecretCapability.SECRET_READ, SecretCapability.SECRET_VERSIONING);
    @Override public String providerType() { return "barbican"; }
    @Override public Set<SecretCapability> capabilities() { return CAPABILITIES; }
    @Override public SecretBootstrapClient create(BootstrapSecretProperties properties, SecretBootstrapContext context) { return BarbicanSecretClient.create(context.environment(), properties); }
}
