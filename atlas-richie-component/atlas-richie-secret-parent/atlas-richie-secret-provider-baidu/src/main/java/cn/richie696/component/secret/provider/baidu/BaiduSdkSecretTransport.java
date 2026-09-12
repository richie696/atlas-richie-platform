package cn.richie696.component.secret.provider.baidu;

import cn.richie696.component.secret.api.SecretVersionSelector;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.api.exception.SecretCryptoException;
import cn.richie696.component.secret.provider.common.RemoteProviderProperties;
import cn.richie696.component.secret.provider.common.RemoteSecretTransport;
import com.baidubce.Protocol;
import com.baidubce.auth.DefaultBceCredentials;
import com.baidubce.services.kms.KmsClient;
import com.baidubce.services.kms.KmsClientConfiguration;
import com.baidubce.services.kms.model.DecryptRequest;
import com.baidubce.services.kms.model.EncryptRequest;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Baidu Cloud KMS SDK adapter. Baidu KMS is intentionally exposed as KMS-only. */
final class BaiduSdkSecretTransport implements RemoteSecretTransport {
    private final KmsClient kms;

    BaiduSdkSecretTransport(RemoteProviderProperties properties) {
        this(createKms(properties));
    }

    BaiduSdkSecretTransport(KmsClient kms) {
        this.kms = kms;
    }

    @Override
    public RemoteValue read(String path, SecretVersionSelector selector) {
        throw new SecretConfigurationException("SEC-CAP-001", "Baidu provider does not support SECRET_READ");
    }

    @Override
    public byte[] wrap(String key, byte[] plaintext, CryptoContext context) {
        requireNoAad(context);
        try {
            EncryptRequest request = new EncryptRequest();
            request.setKeyId(key);
            request.setPlaintext(Base64.getEncoder().encodeToString(plaintext));
            return kms.encrypt(request).getCiphertext().getBytes(StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new SecretCryptoException("SEC-CRYPTO-001", "Baidu Cloud KMS key wrap failed", exception);
        }
    }

    @Override
    public byte[] unwrap(String key, byte[] wrapped, CryptoContext context) {
        requireNoAad(context);
        try {
            DecryptRequest request = new DecryptRequest();
            request.setKeyId(key);
            request.setCiphertext(new String(wrapped, StandardCharsets.UTF_8));
            return Base64.getDecoder().decode(kms.decrypt(request).getPlaintext());
        } catch (Exception exception) {
            throw new SecretCryptoException("SEC-CRYPTO-002", "Baidu Cloud KMS key unwrap failed", exception);
        }
    }

    private static KmsClient createKms(RemoteProviderProperties properties) {
        RemoteProviderProperties.Authentication authentication = properties.getAuthentication();
        if (authentication.getType() != RemoteProviderProperties.AuthenticationType.ACCESS_KEY) {
            throw new SecretConfigurationException("SEC-BOOT-003",
                    "Baidu Cloud KMS SDK requires access-key authentication");
        }
        char[] secret = authentication.getAccessKeySecret();
        try {
            URI endpoint = properties.getKmsEndpoint();
            KmsClientConfiguration configuration = new KmsClientConfiguration();
            configuration.setEndpoint(endpoint.getHost() + (endpoint.getPort() < 0 ? "" : ":" + endpoint.getPort()));
            configuration.setProtocol("http".equalsIgnoreCase(endpoint.getScheme()) ? Protocol.HTTP : Protocol.HTTPS);
            configuration.setCredentials(new DefaultBceCredentials(authentication.getAccessKeyId(), new String(secret)));
            return new KmsClient(configuration);
        } finally {
            java.util.Arrays.fill(secret, '\0');
        }
    }
    private static void requireNoAad(CryptoContext context) {
        if (context == null) return;
        byte[] associatedData = context.associatedData();
        try {
            if (associatedData.length != 0 || !context.attributes().isEmpty()) {
                throw new SecretConfigurationException("SEC-CAP-001", "Baidu Cloud KMS wrap does not support CryptoContext AAD");
            }
        } finally { java.util.Arrays.fill(associatedData, (byte) 0); }
    }
}
