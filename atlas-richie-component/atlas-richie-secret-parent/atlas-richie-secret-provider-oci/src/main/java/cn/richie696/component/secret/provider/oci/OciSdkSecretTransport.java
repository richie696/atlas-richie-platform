package cn.richie696.component.secret.provider.oci;

import cn.richie696.component.secret.api.SecretVersionSelector;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.api.exception.SecretCryptoException;
import cn.richie696.component.secret.api.exception.SecretException;
import cn.richie696.component.secret.provider.common.RemoteProviderProperties;
import cn.richie696.component.secret.provider.common.RemoteSecretTransport;
import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider;
import com.oracle.bmc.keymanagement.KmsCryptoClient;
import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.keymanagement.model.DecryptDataDetails;
import com.oracle.bmc.keymanagement.model.EncryptDataDetails;
import com.oracle.bmc.keymanagement.requests.DecryptRequest;
import com.oracle.bmc.keymanagement.requests.EncryptRequest;
import com.oracle.bmc.secrets.SecretsClient;
import com.oracle.bmc.secrets.model.Base64SecretBundleContentDetails;
import com.oracle.bmc.secrets.requests.GetSecretBundleRequest;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** OCI Vault and KMS SDK adapter using OCI Instance or Resource Principal credential chains. */
final class OciSdkSecretTransport implements RemoteSecretTransport {
    private final SecretsClient secrets;
    private final KmsCryptoClient kms;
    OciSdkSecretTransport(RemoteProviderProperties properties) {
        AbstractAuthenticationDetailsProvider principal = principal(properties);
        this.secrets = SecretsClient.builder().endpoint(properties.getSecretEndpoint().toString()).build(principal);
        this.kms = KmsCryptoClient.builder().endpoint(properties.getKmsEndpoint().toString()).build(principal);
    }
    OciSdkSecretTransport(SecretsClient secrets, KmsCryptoClient kms) {
        this.secrets = secrets;
        this.kms = kms;
    }
    @Override public RemoteValue read(String path, SecretVersionSelector selector) {
        try {
            GetSecretBundleRequest.Builder request = GetSecretBundleRequest.builder().secretId(path);
            if (selector != null && selector.type() == SecretVersionSelector.Type.VERSION) request.versionNumber(Long.valueOf(selector.value()));
            if (selector != null && selector.type() == SecretVersionSelector.Type.STAGE) {
                request.stage(GetSecretBundleRequest.Stage.create(selector.value().toUpperCase(java.util.Locale.ROOT)));
            }
            if (selector != null && selector.type() == SecretVersionSelector.Type.ALIAS) request.secretVersionName(selector.value());
            var bundle = secrets.getSecretBundle(request.build()).getSecretBundle();
            if (!(bundle.getSecretBundleContent() instanceof Base64SecretBundleContentDetails content)) {
                throw new SecretException("SEC-PROVIDER-001", "OCI Vault returned unsupported Secret content");
            }
            return new RemoteValue(Base64.getDecoder().decode(content.getContent()), bundle.getVersionName(),
                    bundle.getTimeCreated() == null ? null : bundle.getTimeCreated().toInstant(), Map.of());
        } catch (BmcException exception) {
            if (exception.getStatusCode() == 404) return null;
            throw new SecretException("SEC-PROVIDER-001", "OCI Vault Secret read failed", exception);
        } catch (SecretException exception) { throw exception;
        } catch (RuntimeException exception) { throw new SecretException("SEC-PROVIDER-001", "OCI Vault Secret read failed", exception); }
    }
    @Override public byte[] wrap(String key, byte[] plaintext, CryptoContext context) {
        try {
            EncryptDataDetails details = EncryptDataDetails.builder().keyId(key)
                    .plaintext(Base64.getEncoder().encodeToString(plaintext)).associatedData(aad(context)).build();
            return kms.encrypt(EncryptRequest.builder().encryptDataDetails(details).build()).getEncryptedData().getCiphertext().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } catch (RuntimeException exception) { throw new SecretCryptoException("SEC-CRYPTO-001", "OCI KMS key wrap failed", exception); }
    }
    @Override public byte[] unwrap(String key, byte[] wrapped, CryptoContext context) {
        try {
            DecryptDataDetails details = DecryptDataDetails.builder().keyId(key)
                    .ciphertext(new String(wrapped, java.nio.charset.StandardCharsets.UTF_8)).associatedData(aad(context)).build();
            return Base64.getDecoder().decode(kms.decrypt(DecryptRequest.builder().decryptDataDetails(details).build()).getDecryptedData().getPlaintext());
        } catch (RuntimeException exception) { throw new SecretCryptoException("SEC-CRYPTO-002", "OCI KMS key unwrap failed", exception); }
    }
    private static AbstractAuthenticationDetailsProvider principal(RemoteProviderProperties properties) {
        return switch (properties.getAuthentication().getType()) {
            case NONE -> InstancePrincipalsAuthenticationDetailsProvider.builder().build();
            case WORKLOAD_IDENTITY_TOKEN_FILE -> ResourcePrincipalAuthenticationDetailsProvider.builder().build();
            default -> throw new SecretConfigurationException("SEC-BOOT-003", "OCI SDK requires Instance or Resource Principal authentication");
        };
    }
    private static Map<String, String> aad(CryptoContext context) {
        if (context == null) return Map.of();
        Map<String, String> values = new LinkedHashMap<>(context.attributes());
        byte[] bytes = context.associatedData();
        try { if (bytes.length > 0) values.put("atlas.secret.aad", Base64.getEncoder().encodeToString(bytes)); }
        finally { java.util.Arrays.fill(bytes, (byte) 0); }
        return Map.copyOf(values);
    }
}
