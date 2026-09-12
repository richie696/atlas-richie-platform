package cn.richie696.component.secret.provider.huawei;

import cn.richie696.component.secret.api.SecretVersionSelector;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.api.exception.SecretCryptoException;
import cn.richie696.component.secret.api.exception.SecretException;
import cn.richie696.component.secret.provider.common.RemoteProviderProperties;
import cn.richie696.component.secret.provider.common.RemoteSecretTransport;
import com.huaweicloud.sdk.core.ClientBuilder;
import com.huaweicloud.sdk.core.auth.BasicCredentials;
import com.huaweicloud.sdk.core.exception.ServiceResponseException;
import com.huaweicloud.sdk.csms.v1.CsmsClient;
import com.huaweicloud.sdk.csms.v1.model.ShowSecretVersionRequest;
import com.huaweicloud.sdk.csms.v1.model.ShowSecretVersionResponse;
import com.huaweicloud.sdk.csms.v1.model.Version;
import com.huaweicloud.sdk.csms.v1.model.VersionMetadata;
import com.huaweicloud.sdk.kms.v2.KmsClient;
import com.huaweicloud.sdk.kms.v2.model.DecryptDataRequest;
import com.huaweicloud.sdk.kms.v2.model.DecryptDataRequestBody;
import com.huaweicloud.sdk.kms.v2.model.DecryptDataResponse;
import com.huaweicloud.sdk.kms.v2.model.EncryptDataRequest;
import com.huaweicloud.sdk.kms.v2.model.EncryptDataRequestBody;
import com.huaweicloud.sdk.kms.v2.model.EncryptDataResponse;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Huawei Cloud CSMS and DEW KMS SDK adapter; SDK signing remains inside the vendor client. */
final class HuaweiSdkSecretTransport implements RemoteSecretTransport {
    private final CsmsClient csms;
    private final KmsClient kms;

    HuaweiSdkSecretTransport(RemoteProviderProperties properties) {
        this(createCsms(properties), createKms(properties));
    }

    HuaweiSdkSecretTransport(CsmsClient csms, KmsClient kms) {
        this.csms = csms;
        this.kms = kms;
    }

    @Override
    public RemoteValue read(String path, SecretVersionSelector selector) {
        try {
            ShowSecretVersionRequest request = new ShowSecretVersionRequest().withSecretName(path);
            if (selector != null && selector.type() != SecretVersionSelector.Type.LATEST) {
                request.withVersionId(selector.value());
            }
            ShowSecretVersionResponse response = csms.showSecretVersion(request);
            Version version = response.getVersion();
            if (version == null) {
                throw new SecretException("SEC-PROVIDER-001", "Huawei Cloud CSMS returned no Secret version");
            }
            VersionMetadata metadata = version.getVersionMetadata();
            return new RemoteValue(value(version), metadata == null ? null : metadata.getId(),
                    metadata == null || metadata.getCreateTime() == null
                            ? null : Instant.ofEpochMilli(metadata.getCreateTime()),
                    Map.of());
        } catch (ServiceResponseException exception) {
            if (exception.getHttpStatusCode() == 404) {
                return null;
            }
            throw new SecretException("SEC-PROVIDER-001", "Huawei Cloud CSMS read failed", exception);
        } catch (SecretException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SecretException("SEC-PROVIDER-001", "Huawei Cloud CSMS read failed", exception);
        }
    }

    @Override
    public byte[] wrap(String key, byte[] plaintext, CryptoContext context) {
        try {
            EncryptDataRequestBody body = new EncryptDataRequestBody()
                    .withKeyId(key)
                    .withPlainText(Base64.getEncoder().encodeToString(plaintext));
            String aad = aad(context);
            if (aad != null) {
                body.withAdditionalAuthenticatedData(aad);
            }
            EncryptDataResponse response = kms.encryptData(new EncryptDataRequest().withBody(body));
            return response.getCipherText().getBytes(StandardCharsets.UTF_8);
        } catch (RuntimeException exception) {
            throw new SecretCryptoException("SEC-CRYPTO-001", "Huawei Cloud KMS key wrap failed", exception);
        }
    }

    @Override
    public byte[] unwrap(String key, byte[] wrapped, CryptoContext context) {
        try {
            DecryptDataRequestBody body = new DecryptDataRequestBody()
                    .withCipherText(new String(wrapped, StandardCharsets.UTF_8));
            String aad = aad(context);
            if (aad != null) {
                body.withAdditionalAuthenticatedData(aad);
            }
            DecryptDataResponse response = kms.decryptData(new DecryptDataRequest().withBody(body));
            String encoded = response.getPlainTextBase64();
            return encoded == null || encoded.isBlank()
                    ? response.getPlainText().getBytes(StandardCharsets.UTF_8)
                    : Base64.getDecoder().decode(encoded);
        } catch (RuntimeException exception) {
            throw new SecretCryptoException("SEC-CRYPTO-002", "Huawei Cloud KMS key unwrap failed", exception);
        }
    }

    private static CsmsClient createCsms(RemoteProviderProperties properties) {
        return client(CsmsClient.newBuilder(), properties.getSecretEndpoint().toString(), credentials(properties));
    }

    private static KmsClient createKms(RemoteProviderProperties properties) {
        return client(KmsClient.newBuilder(), properties.getKmsEndpoint().toString(), credentials(properties));
    }

    private static <T> T client(ClientBuilder<T> builder, String endpoint, BasicCredentials credentials) {
        return builder.withCredential(credentials).withEndpoint(endpoint).build();
    }

    private static BasicCredentials credentials(RemoteProviderProperties properties) {
        RemoteProviderProperties.Authentication authentication = properties.getAuthentication();
        if (authentication.getType() != RemoteProviderProperties.AuthenticationType.ACCESS_KEY) {
            throw new SecretConfigurationException("SEC-BOOT-003",
                    "Huawei Cloud SDK requires access-key authentication");
        }
        char[] secret = authentication.getAccessKeySecret();
        try {
            return new BasicCredentials()
                    .withAk(authentication.getAccessKeyId())
                    .withSk(new String(secret))
                    .withSecurityToken(authentication.getSecurityToken())
                    .withProjectId(required(properties.getProjectId(), "Huawei Cloud project-id"));
        } finally {
            java.util.Arrays.fill(secret, '\0');
        }
    }

    private static Object value(Version version) {
        if (version.getSecretBinary() != null && !version.getSecretBinary().isBlank()) {
            return Base64.getDecoder().decode(version.getSecretBinary());
        }
        return version.getSecretString();
    }

    private static String aad(CryptoContext context) {
        byte[] associatedData = context == null ? new byte[0] : context.associatedData();
        try {
            return associatedData.length == 0 ? null : Base64.getEncoder().encodeToString(associatedData);
        } finally {
            java.util.Arrays.fill(associatedData, (byte) 0);
        }
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new SecretConfigurationException("SEC-BOOT-003", label + " is required");
        }
        return value;
    }
}
