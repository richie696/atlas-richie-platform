package cn.richie696.component.secret.provider.azure;

import cn.richie696.component.secret.api.SecretVersionSelector;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.exception.SecretCryptoException;
import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.api.exception.SecretException;
import cn.richie696.component.secret.provider.common.RemoteProviderProperties;
import cn.richie696.component.secret.provider.common.RemoteSecretTransport;
import com.azure.core.exception.ResourceNotFoundException;
import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.keys.cryptography.CryptographyClient;
import com.azure.security.keyvault.keys.cryptography.CryptographyClientBuilder;
import com.azure.security.keyvault.keys.cryptography.models.KeyWrapAlgorithm;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Azure Key Vault SDK adapter. Azure identity resolution remains inside the vendor SDK. */
final class AzureSdkSecretTransport implements RemoteSecretTransport {
    private final TokenCredential credential = new DefaultAzureCredentialBuilder().build();
    private final SecretClient secrets;
    private final Function<String, CryptographyClient> cryptographyClientFactory;
    private final Map<String, CryptographyClient> cryptographyClients = new ConcurrentHashMap<>();

    AzureSdkSecretTransport(RemoteProviderProperties properties) {
        this.secrets = new SecretClientBuilder()
                .vaultUrl(properties.getSecretEndpoint().toString())
                .credential(credential)
                .buildClient();
        this.cryptographyClientFactory = this::newCryptographyClient;
    }

    AzureSdkSecretTransport(SecretClient secrets, Function<String, CryptographyClient> cryptographyClientFactory) {
        this.secrets = secrets;
        this.cryptographyClientFactory = cryptographyClientFactory;
    }

    @Override
    public RemoteValue read(String path, SecretVersionSelector selector) {
        try {
            KeyVaultSecret secret = selector == null || selector.type() == SecretVersionSelector.Type.LATEST
                    ? secrets.getSecret(path) : secrets.getSecret(path, selector.value());
            Instant createdAt = secret.getProperties().getCreatedOn() == null
                    ? null : secret.getProperties().getCreatedOn().toInstant();
            return new RemoteValue(secret.getValue(), secret.getProperties().getVersion(), createdAt,
                    secret.getProperties().getTags());
        } catch (ResourceNotFoundException ignored) {
            return null;
        } catch (RuntimeException exception) {
            throw new SecretException("SEC-PROVIDER-001", "Azure Key Vault Secret read failed", exception);
        }
    }

    @Override
    public byte[] wrap(String key, byte[] plaintext, CryptoContext context) {
        requireNoAad(context);
        try {
            return crypto(key).wrapKey(KeyWrapAlgorithm.RSA_OAEP_256, plaintext).getEncryptedKey();
        } catch (RuntimeException exception) {
            throw new SecretCryptoException("SEC-CRYPTO-001", "Azure Key Vault key wrap failed", exception);
        }
    }

    @Override
    public byte[] unwrap(String key, byte[] wrapped, CryptoContext context) {
        requireNoAad(context);
        try {
            return crypto(key).unwrapKey(KeyWrapAlgorithm.RSA_OAEP_256, wrapped).getKey();
        } catch (RuntimeException exception) {
            throw new SecretCryptoException("SEC-CRYPTO-002", "Azure Key Vault key unwrap failed", exception);
        }
    }

    private CryptographyClient crypto(String keyIdentifier) {
        return cryptographyClients.computeIfAbsent(keyIdentifier, cryptographyClientFactory);
    }

    private CryptographyClient newCryptographyClient(String identifier) {
        return new CryptographyClientBuilder()
                .keyIdentifier(identifier).credential(credential).buildClient();
    }

    private static void requireNoAad(CryptoContext context) {
        if (context == null) return;
        byte[] associatedData = context.associatedData();
        try {
            if (associatedData.length != 0 || !context.attributes().isEmpty()) {
                throw new SecretConfigurationException("SEC-CAP-001", "Azure Key Vault RSA wrap does not support CryptoContext AAD");
            }
        } finally { java.util.Arrays.fill(associatedData, (byte) 0); }
    }
}
