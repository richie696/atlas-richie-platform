package cn.richie696.component.secret.provider.volcengine;

import cn.richie696.component.secret.api.SecretVersionSelector;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.api.exception.SecretCryptoException;
import cn.richie696.component.secret.provider.common.RemoteProviderProperties;
import cn.richie696.component.secret.provider.common.RemoteSecretTransport;
import com.volcengine.ApiClient;
import com.volcengine.auth.CredentialProvider;
import com.volcengine.auth.StaticCredentialProvider;
import com.volcengine.kms.KmsApi;
import com.volcengine.kms.model.DecryptRequest;
import com.volcengine.kms.model.EncryptRequest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Volcengine KMS SDK adapter. This Provider deliberately remains KMS-only. */
final class VolcengineSdkSecretTransport implements RemoteSecretTransport {
    private final KmsApi kms;
    private final String keyringName;

    VolcengineSdkSecretTransport(RemoteProviderProperties properties) {
        this(createKms(properties), required(properties.getNamespace(), "Volcengine keyring namespace"));
    }

    VolcengineSdkSecretTransport(KmsApi kms, String keyringName) {
        this.kms = kms;
        this.keyringName = keyringName;
    }

    @Override
    public RemoteValue read(String path, SecretVersionSelector selector) {
        throw new SecretConfigurationException("SEC-CAP-001", "Volcengine provider does not support SECRET_READ");
    }

    @Override
    public byte[] wrap(String key, byte[] plaintext, CryptoContext context) {
        try {
            EncryptRequest request = new EncryptRequest()
                    .keyringName(keyringName)
                    .keyName(key)
                    .plaintext(Base64.getEncoder().encodeToString(plaintext))
                    .encryptionContext(encryptionContext(context));
            return kms.encrypt(request).getCiphertextBlob().getBytes(StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new SecretCryptoException("SEC-CRYPTO-001", "Volcengine KMS key wrap failed", exception);
        }
    }

    @Override
    public byte[] unwrap(String key, byte[] wrapped, CryptoContext context) {
        try {
            DecryptRequest request = new DecryptRequest()
                    .ciphertextBlob(new String(wrapped, StandardCharsets.UTF_8))
                    .encryptionContext(encryptionContext(context));
            return Base64.getDecoder().decode(kms.decrypt(request).getPlaintext());
        } catch (Exception exception) {
            throw new SecretCryptoException("SEC-CRYPTO-002", "Volcengine KMS key unwrap failed", exception);
        }
    }

    private static KmsApi createKms(RemoteProviderProperties properties) {
        RemoteProviderProperties.Authentication authentication = properties.getAuthentication();
        if (authentication.getType() != RemoteProviderProperties.AuthenticationType.ACCESS_KEY) {
            throw new SecretConfigurationException("SEC-BOOT-003",
                    "Volcengine KMS SDK requires access-key authentication");
        }
        char[] secret = authentication.getAccessKeySecret();
        try {
            ApiClient client = new ApiClient()
                    .setEndpoint(properties.getKmsEndpoint().toString())
                    .setRegion(required(properties.getRegion(), "Volcengine region"))
                    .setCredentialProvider(new CredentialProvider(new StaticCredentialProvider(
                            authentication.getAccessKeyId(), new String(secret), authentication.getSecurityToken())));
            return new KmsApi(client);
        } finally {
            java.util.Arrays.fill(secret, '\0');
        }
    }

    private static Map<String, String> encryptionContext(CryptoContext context) {
        Map<String, String> values = new LinkedHashMap<>();
        if (context != null) {
            values.putAll(context.attributes());
            byte[] associatedData = context.associatedData();
            try {
                if (associatedData.length > 0) {
                    values.put("atlas.secret.aad", Base64.getEncoder().encodeToString(associatedData));
                }
            } finally {
                java.util.Arrays.fill(associatedData, (byte) 0);
            }
        }
        return Map.copyOf(values);
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new SecretConfigurationException("SEC-BOOT-003", label + " is required");
        }
        return value;
    }
}
