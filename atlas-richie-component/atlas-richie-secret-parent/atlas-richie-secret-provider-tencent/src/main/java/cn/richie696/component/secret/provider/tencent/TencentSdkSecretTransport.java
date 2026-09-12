package cn.richie696.component.secret.provider.tencent;

import cn.richie696.component.secret.api.SecretVersionSelector;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.exception.SecretCryptoException;
import cn.richie696.component.secret.api.exception.SecretException;
import cn.richie696.component.secret.provider.common.RemoteProviderProperties;
import cn.richie696.component.secret.provider.common.RemoteSecretTransport;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.kms.v20190118.KmsClient;
import com.tencentcloudapi.kms.v20190118.models.DecryptRequest;
import com.tencentcloudapi.kms.v20190118.models.EncryptRequest;
import com.tencentcloudapi.ssm.v20190923.SsmClient;
import com.tencentcloudapi.ssm.v20190923.models.GetSecretValueRequest;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/** Tencent Cloud SSM and KMS SDK adapter; TC3 signing is supplied by the SDK. */
final class TencentSdkSecretTransport implements RemoteSecretTransport {
    private final SsmClient ssm;
    private final KmsClient kms;

    TencentSdkSecretTransport(RemoteProviderProperties properties) {
        char[] secret = properties.getAuthentication().getAccessKeySecret();
        try {
            Credential credential = new Credential(properties.getAuthentication().getAccessKeyId(), new String(secret),
                    properties.getAuthentication().getSecurityToken());
            this.ssm = new SsmClient(credential, properties.getRegion(), profile(properties.getSecretEndpoint()));
            this.kms = new KmsClient(credential, properties.getRegion(), profile(properties.getKmsEndpoint()));
        } finally {
            java.util.Arrays.fill(secret, '\0');
        }
    }

    TencentSdkSecretTransport(SsmClient ssm, KmsClient kms) {
        this.ssm = ssm;
        this.kms = kms;
    }

    @Override
    public RemoteValue read(String path, SecretVersionSelector selector) {
        try {
            GetSecretValueRequest request = new GetSecretValueRequest();
            request.setSecretName(path);
            if (selector != null && selector.type() != SecretVersionSelector.Type.LATEST) request.setVersionId(selector.value());
            var response = ssm.GetSecretValue(request);
            byte[] value = response.getSecretBinary() == null || response.getSecretBinary().isBlank()
                    ? response.getSecretString().getBytes(StandardCharsets.UTF_8)
                    : Base64.getDecoder().decode(response.getSecretBinary());
            return new RemoteValue(value, response.getVersionId(), Instant.now(), Map.of("requestId", response.getRequestId()));
        } catch (TencentCloudSDKException exception) {
            if ("ResourceNotFound".equalsIgnoreCase(exception.getErrorCode())) return null;
            throw new SecretException("SEC-PROVIDER-001", "Tencent Cloud SSM read failed", exception);
        } catch (RuntimeException exception) {
            throw new SecretException("SEC-PROVIDER-001", "Tencent Cloud SSM read failed", exception);
        }
    }

    @Override public byte[] wrap(String key, byte[] plaintext, CryptoContext context) {
        try {
            EncryptRequest request = new EncryptRequest(); request.setKeyId(key);
            request.setPlaintext(Base64.getEncoder().encodeToString(plaintext));
            request.setEncryptionContext(encryptionContext(context));
            var response = kms.Encrypt(request);
            return response.getCiphertextBlob().getBytes(StandardCharsets.UTF_8);
        } catch (TencentCloudSDKException exception) {
            throw new SecretCryptoException("SEC-CRYPTO-001", "Tencent Cloud KMS key wrap failed", exception);
        }
    }

    @Override public byte[] unwrap(String key, byte[] wrapped, CryptoContext context) {
        try {
            DecryptRequest request = new DecryptRequest();
            request.setCiphertextBlob(new String(wrapped, StandardCharsets.UTF_8));
            request.setEncryptionContext(encryptionContext(context));
            var response = kms.Decrypt(request);
            return Base64.getDecoder().decode(response.getPlaintext());
        } catch (TencentCloudSDKException exception) {
            throw new SecretCryptoException("SEC-CRYPTO-002", "Tencent Cloud KMS key unwrap failed", exception);
        }
    }

    private ClientProfile profile(java.net.URI endpoint) {
        HttpProfile http = new HttpProfile(); http.setEndpoint(endpoint.getHost());
        ClientProfile profile = new ClientProfile(); profile.setHttpProfile(http); return profile;
    }

    private static String encryptionContext(CryptoContext context) {
        if (context == null) return null;
        byte[] associatedData = context.associatedData();
        try {
            StringBuilder value = new StringBuilder("atlas.secret.aad=")
                    .append(Base64.getEncoder().encodeToString(associatedData));
            context.attributes().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> value.append('\n').append(entry.getKey()).append('=').append(entry.getValue()));
            return value.toString();
        } finally {
            java.util.Arrays.fill(associatedData, (byte) 0);
        }
    }
}
