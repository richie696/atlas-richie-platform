package cn.richie696.component.secret.provider.ibm;

import cn.richie696.component.secret.api.SecretVersionSelector;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.api.exception.SecretCryptoException;
import cn.richie696.component.secret.provider.common.RemoteProviderProperties;
import cn.richie696.component.secret.provider.common.RemoteSecretTransport;
import com.ibm.cloud.ibm_key_protect_api.v2.IbmKeyProtectApi;
import com.ibm.cloud.ibm_key_protect_api.v2.model.UnwrapKeyOptions;
import com.ibm.cloud.ibm_key_protect_api.v2.model.WrapKeyOptions;
import com.ibm.cloud.sdk.core.security.BearerTokenAuthenticator;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** IBM Key Protect SDK adapter. tenant-id is the Key Protect instance ID; namespace is the key ring. */
final class IbmKeyProtectSdkSecretTransport implements RemoteSecretTransport {
    private final IbmKeyProtectApi api;
    private final String instanceId;
    private final String keyRing;

    IbmKeyProtectSdkSecretTransport(RemoteProviderProperties properties) {
        this(createApi(properties), required(properties.getTenantId(), "IBM Key Protect instance-id"),
                properties.getNamespace() == null || properties.getNamespace().isBlank() ? "default" : properties.getNamespace());
    }
    IbmKeyProtectSdkSecretTransport(IbmKeyProtectApi api, String instanceId, String keyRing) {
        this.api = api; this.instanceId = instanceId; this.keyRing = keyRing;
    }
    @Override public RemoteValue read(String path, SecretVersionSelector selector) {
        throw new SecretConfigurationException("SEC-CAP-001", "IBM Key Protect provider does not support SECRET_READ");
    }
    @Override public byte[] wrap(String key, byte[] plaintext, CryptoContext context) {
        requireNoAad(context);
        try {
            String encoded = Base64.getEncoder().encodeToString(plaintext);
            WrapKeyOptions request = new WrapKeyOptions.Builder(key, instanceId)
                    .xKmsKeyRing(keyRing).keyActionWrapBody(body("plaintext", encoded)).build();
            return api.wrapKey(request).execute().getResult().getCiphertext().getBytes(StandardCharsets.UTF_8);
        } catch (RuntimeException exception) {
            throw new SecretCryptoException("SEC-CRYPTO-001", "IBM Key Protect key wrap failed", exception);
        }
    }
    @Override public byte[] unwrap(String key, byte[] wrapped, CryptoContext context) {
        requireNoAad(context);
        try {
            String ciphertext = new String(wrapped, StandardCharsets.UTF_8);
            UnwrapKeyOptions request = new UnwrapKeyOptions.Builder(key, instanceId, body("ciphertext", ciphertext))
                    .xKmsKeyRing(keyRing).build();
            return Base64.getDecoder().decode(api.unwrapKey(request).execute().getResult().getPlaintext());
        } catch (RuntimeException exception) {
            throw new SecretCryptoException("SEC-CRYPTO-002", "IBM Key Protect key unwrap failed", exception);
        }
    }
    private static IbmKeyProtectApi createApi(RemoteProviderProperties properties) {
        RemoteProviderProperties.Authentication auth = properties.getAuthentication();
        if (auth.getType() != RemoteProviderProperties.AuthenticationType.BEARER_TOKEN) {
            throw new SecretConfigurationException("SEC-BOOT-003", "IBM Key Protect SDK requires bearer-token authentication");
        }
        char[] token = auth.getToken();
        try {
            IbmKeyProtectApi api = new IbmKeyProtectApi("ibm-key-protect", new BearerTokenAuthenticator(new String(token)));
            api.setServiceUrl(properties.getKmsEndpoint().toString());
            return api;
        } finally { java.util.Arrays.fill(token, '\0'); }
    }
    private static ByteArrayInputStream body(String name, String value) {
        return new ByteArrayInputStream(("{\"" + name + "\":\"" + value + "\"}").getBytes(StandardCharsets.UTF_8));
    }
    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new SecretConfigurationException("SEC-BOOT-003", label + " is required");
        return value;
    }
    private static void requireNoAad(CryptoContext context) {
        if (context == null) return;
        byte[] associatedData = context.associatedData();
        try {
            if (associatedData.length != 0 || !context.attributes().isEmpty()) {
                throw new SecretConfigurationException("SEC-CAP-001", "IBM Key Protect wrap does not support CryptoContext AAD");
            }
        } finally { java.util.Arrays.fill(associatedData, (byte) 0); }
    }
}
