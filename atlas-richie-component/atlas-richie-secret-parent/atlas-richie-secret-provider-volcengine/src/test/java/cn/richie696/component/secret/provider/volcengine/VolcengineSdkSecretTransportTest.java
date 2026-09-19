package cn.richie696.component.secret.provider.volcengine;

import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.SecretVersionSelector;
import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.api.exception.SecretCryptoException;
import cn.richie696.component.secret.provider.common.RemoteProviderProperties;
import com.volcengine.kms.KmsApi;
import com.volcengine.kms.model.DecryptRequest;
import com.volcengine.kms.model.DecryptResponse;
import com.volcengine.kms.model.EncryptRequest;
import com.volcengine.kms.model.EncryptResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VolcengineSdkSecretTransportTest {
    @Test
    void mapsKeyringPlaintextAndEncryptionContextThroughTheOfficialSdkModels() throws Exception {
        KmsApi kms = mock(KmsApi.class);
        when(kms.encrypt(any())).thenReturn(new EncryptResponse().ciphertextBlob("ciphertext"));
        when(kms.decrypt(any())).thenReturn(new DecryptResponse()
                .plaintext(Base64.getEncoder().encodeToString("data-key".getBytes(StandardCharsets.UTF_8))));
        VolcengineSdkSecretTransport transport = new VolcengineSdkSecretTransport(kms, "orders");
        byte[] plaintext = "data-key".getBytes(StandardCharsets.UTF_8);
        CryptoContext context = new CryptoContext("orders:42".getBytes(StandardCharsets.UTF_8), Map.of("tenant", "tenant-a"));

        byte[] wrapped = transport.wrap("envelope", plaintext, context);
        byte[] unwrapped = transport.unwrap("envelope", wrapped, context);

        ArgumentCaptor<EncryptRequest> encrypt = ArgumentCaptor.forClass(EncryptRequest.class);
        ArgumentCaptor<DecryptRequest> decrypt = ArgumentCaptor.forClass(DecryptRequest.class);
        org.mockito.Mockito.verify(kms).encrypt(encrypt.capture());
        org.mockito.Mockito.verify(kms).decrypt(decrypt.capture());
        assertThat(encrypt.getValue().getKeyringName()).isEqualTo("orders");
        assertThat(encrypt.getValue().getKeyName()).isEqualTo("envelope");
        assertThat(encrypt.getValue().getPlaintext())
                .isEqualTo(Base64.getEncoder().encodeToString(plaintext));
        assertThat(encrypt.getValue().getEncryptionContext())
                .containsEntry("tenant", "tenant-a")
                .containsEntry("atlas.secret.aad", Base64.getEncoder().encodeToString("orders:42".getBytes(StandardCharsets.UTF_8)));
        assertThat(decrypt.getValue().getCiphertextBlob()).isEqualTo("ciphertext");
        assertThat(decrypt.getValue().getEncryptionContext()).isEqualTo(encrypt.getValue().getEncryptionContext());
        assertThat(unwrapped).isEqualTo(plaintext);
    }

    @Test
    void remainsKmsOnlyAndMapsSdkFailures() throws Exception {
        KmsApi kms = mock(KmsApi.class);
        when(kms.encrypt(any())).thenThrow(new IllegalStateException("encrypt"));
        when(kms.decrypt(any())).thenThrow(new IllegalStateException("decrypt"));
        VolcengineSdkSecretTransport transport = new VolcengineSdkSecretTransport(kms, "orders");

        assertThatThrownBy(() -> transport.read("secret", SecretVersionSelector.latest()))
                .isInstanceOf(SecretConfigurationException.class).hasMessageContaining("SECRET_READ");
        assertThatThrownBy(() -> transport.wrap("key", new byte[]{1}, null))
                .isInstanceOf(SecretCryptoException.class).hasMessageContaining("wrap failed");
        assertThatThrownBy(() -> transport.unwrap("key", new byte[]{1}, CryptoContext.empty()))
                .isInstanceOf(SecretCryptoException.class).hasMessageContaining("unwrap failed");
    }

    @Test
    void rejectsMissingAccessKeyAuthenticationBeforeCreatingSdkClient() {
        RemoteProviderProperties properties = new RemoteProviderProperties();
        properties.setEndpoint(java.net.URI.create("https://volcengine.example"));
        properties.setNamespace("orders");
        properties.setRegion("cn-beijing");
        properties.getAuthentication().setType(RemoteProviderProperties.AuthenticationType.NONE);

        assertThatThrownBy(() -> new VolcengineSdkSecretTransport(properties))
                .isInstanceOf(SecretConfigurationException.class).hasMessageContaining("access-key");
    }
}
