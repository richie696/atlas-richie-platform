package cn.richie696.component.secret.provider.gcp;

import cn.richie696.component.secret.api.SecretVersionSelector;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.api.exception.SecretCryptoException;
import cn.richie696.component.secret.api.exception.SecretException;
import cn.richie696.component.secret.provider.common.RemoteProviderProperties;
import cn.richie696.component.secret.provider.common.RemoteSecretTransport;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.cloud.kms.v1.DecryptRequest;
import com.google.cloud.kms.v1.EncryptRequest;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/** Google Cloud Secret Manager and Cloud KMS SDK adapter using ADC/workload identity. */
final class GcpSdkSecretTransport implements RemoteSecretTransport {
    private final String projectId;
    private final SecretManagerServiceClient secrets;
    private final KeyManagementServiceClient kms;

    GcpSdkSecretTransport(RemoteProviderProperties properties) {
        this.projectId = required(properties.getProjectId(), "GCP project-id");
        try {
            this.secrets = SecretManagerServiceClient.create();
            this.kms = KeyManagementServiceClient.create();
        } catch (IOException exception) {
            throw new SecretConfigurationException("SEC-PROVIDER-001", "GCP SDK clients cannot be created", exception);
        }
    }

    GcpSdkSecretTransport(String projectId, SecretManagerServiceClient secrets, KeyManagementServiceClient kms) {
        this.projectId = required(projectId, "GCP project-id");
        this.secrets = secrets;
        this.kms = kms;
    }

    @Override
    public RemoteValue read(String path, SecretVersionSelector selector) {
        try {
            String version = selector == null || selector.type() == SecretVersionSelector.Type.LATEST
                    ? "latest" : selector.value();
            AccessSecretVersionResponse response = secrets.accessSecretVersion(secretVersion(path, version));
            return new RemoteValue(response.getPayload().getData().toByteArray(), version, Instant.now(), Map.of());
        } catch (com.google.api.gax.rpc.NotFoundException ignored) {
            return null;
        } catch (RuntimeException exception) {
            throw new SecretException("SEC-PROVIDER-001", "GCP Secret Manager read failed", exception);
        }
    }

    @Override
    public byte[] wrap(String key, byte[] plaintext, CryptoContext context) {
        try {
            return kms.encrypt(EncryptRequest.newBuilder().setName(key).setPlaintext(ByteString.copyFrom(plaintext))
                    .setAdditionalAuthenticatedData(aad(context)).build()).getCiphertext().toByteArray();
        } catch (RuntimeException exception) {
            throw new SecretCryptoException("SEC-CRYPTO-001", "GCP Cloud KMS key wrap failed", exception);
        }
    }

    @Override
    public byte[] unwrap(String key, byte[] wrapped, CryptoContext context) {
        try {
            return kms.decrypt(DecryptRequest.newBuilder().setName(key).setCiphertext(ByteString.copyFrom(wrapped))
                    .setAdditionalAuthenticatedData(aad(context)).build()).getPlaintext().toByteArray();
        } catch (RuntimeException exception) {
            throw new SecretCryptoException("SEC-CRYPTO-002", "GCP Cloud KMS key unwrap failed", exception);
        }
    }

    @Override public void close() { secrets.close(); kms.close(); }

    private String secretVersion(String path, String version) {
        String name = path.startsWith("projects/") ? path : "projects/" + projectId + "/secrets/" + path;
        return name.contains("/versions/") ? name : name + "/versions/" + version;
    }
    private ByteString aad(CryptoContext context) {
        if (context == null) return ByteString.EMPTY;
        byte[] associatedData = context.associatedData();
        try { return ByteString.copyFrom(associatedData); }
        finally { java.util.Arrays.fill(associatedData, (byte) 0); }
    }
    private String required(String value, String label) {
        if (value == null || value.isBlank()) throw new SecretConfigurationException("SEC-BOOT-003", label + " is required");
        return value;
    }
}
