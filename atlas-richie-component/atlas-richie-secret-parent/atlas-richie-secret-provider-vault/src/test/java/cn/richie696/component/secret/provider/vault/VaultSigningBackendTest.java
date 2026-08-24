package cn.richie696.component.secret.provider.vault;

import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.crypto.KeyPurpose;
import cn.richie696.component.secret.api.crypto.KeyReference;
import cn.richie696.component.secret.api.crypto.SignatureValue;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import org.junit.jupiter.api.Test;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultTransitOperations;
import org.springframework.vault.core.VaultVersionedKeyValueOperations;
import org.springframework.vault.support.Plaintext;
import org.springframework.vault.support.Signature;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
class VaultSigningBackendTest {

    @Test
    void delegatesSignAndVerifyToTransitWithoutExportingPrivateKey() {
        VaultSecretProperties properties = new VaultSecretProperties();
        properties.setEndpoint(URI.create("http://localhost:8200"));
        properties.getAuthentication().setType(VaultSecretProperties.AuthenticationType.AGENT);
        properties.getTransit().setKeyBindings(java.util.Map.of("oauth.signing", "oauth-rsa"));
        BootstrapSecretProperties bootstrap = new BootstrapSecretProperties();
        bootstrap.setEnabled(true);
        bootstrap.getResilience().setMaxAttempts(1);

        VaultVersionedKeyValueOperations kv = proxy(VaultVersionedKeyValueOperations.class, (method, args) -> null);
        VaultTransitOperations transit = proxy(VaultTransitOperations.class, (method, args) -> {
            if (method.getName().equals("sign")) {
                return Signature.of("vault:v1:opaque");
            }
            if (method.getName().equals("verify")) {
                return true;
            }
            return null;
        });
        VaultOperations vault = proxy(VaultOperations.class, (method, args) -> {
            if (method.getName().equals("opsForVersionedKeyValue")) {
                return kv;
            }
            if (method.getName().equals("opsForTransit")) {
                return transit;
            }
            return null;
        });

        VaultSecretClient client = new VaultSecretClient(
                new VaultSecretConfigurationResolver.ResolvedVaultConfiguration("vault", properties, "hash"),
                bootstrap,
                vault,
                () -> { });
        try {
            KeyReference key = new KeyReference("oauth.signing", "current", KeyPurpose.SIGNING);
            SignatureValue signature = client.sign(key, new byte[]{1, 2}, CryptoContext.empty());

            assertThat(signature.value()).isEqualTo("vault:v1:opaque");
            assertThat(client.verify(key, new byte[]{1, 2}, signature, CryptoContext.empty())).isTrue();
        } finally {
            client.close();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.util.function.BiFunction<java.lang.reflect.Method, Object[], Object> handler) {
        return (T) java.lang.reflect.Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> handler.apply(method, args));
    }
}
