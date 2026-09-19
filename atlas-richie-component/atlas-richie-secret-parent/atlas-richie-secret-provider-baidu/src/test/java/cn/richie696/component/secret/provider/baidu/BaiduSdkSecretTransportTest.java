package cn.richie696.component.secret.provider.baidu;

import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.SecretVersionSelector;
import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.api.exception.SecretCryptoException;
import cn.richie696.component.secret.provider.common.RemoteProviderProperties;
import com.baidubce.services.kms.KmsClient;
import com.baidubce.services.kms.model.DecryptRequest;
import com.baidubce.services.kms.model.DecryptResponse;
import com.baidubce.services.kms.model.EncryptRequest;
import com.baidubce.services.kms.model.EncryptResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BaiduSdkSecretTransportTest {
    @Test
    void mapsKeyIdAndBase64PayloadThroughTheOfficialSdkModels() throws Exception {
        KmsClient kms = mock(KmsClient.class);
        EncryptResponse encrypted = new EncryptResponse();
        encrypted.setCiphertext("ciphertext");
        DecryptResponse decrypted = new DecryptResponse();
        decrypted.setPlaintext(Base64.getEncoder().encodeToString("data-key".getBytes(StandardCharsets.UTF_8)));
        when(kms.encrypt(any())).thenReturn(encrypted);
        when(kms.decrypt(any())).thenReturn(decrypted);
        BaiduSdkSecretTransport transport = new BaiduSdkSecretTransport(kms);
        byte[] plaintext = "data-key".getBytes(StandardCharsets.UTF_8);

        byte[] wrapped = transport.wrap("cmk-id", plaintext, CryptoContext.empty());
        byte[] unwrapped = transport.unwrap("cmk-id", wrapped, CryptoContext.empty());

        ArgumentCaptor<EncryptRequest> encrypt = ArgumentCaptor.forClass(EncryptRequest.class);
        ArgumentCaptor<DecryptRequest> decrypt = ArgumentCaptor.forClass(DecryptRequest.class);
        org.mockito.Mockito.verify(kms).encrypt(encrypt.capture());
        org.mockito.Mockito.verify(kms).decrypt(decrypt.capture());
        assertThat(encrypt.getValue().getKeyId()).isEqualTo("cmk-id");
        assertThat(encrypt.getValue().getPlaintext())
                .isEqualTo(Base64.getEncoder().encodeToString(plaintext));
        assertThat(decrypt.getValue().getKeyId()).isEqualTo("cmk-id");
        assertThat(decrypt.getValue().getCiphertext()).isEqualTo("ciphertext");
        assertThat(unwrapped).isEqualTo(plaintext);
    }

    @Test
    void remainsKmsOnlyAndMapsAadAndSdkFailures() throws Exception {
        KmsClient kms = mock(KmsClient.class);
        when(kms.encrypt(any())).thenThrow(new IllegalStateException("encrypt"));
        when(kms.decrypt(any())).thenThrow(new IllegalStateException("decrypt"));
        BaiduSdkSecretTransport transport = new BaiduSdkSecretTransport(kms);

        assertThatThrownBy(() -> transport.read("secret", SecretVersionSelector.latest()))
                .isInstanceOf(SecretConfigurationException.class).hasMessageContaining("SECRET_READ");
        assertThatThrownBy(() -> transport.wrap("key", new byte[]{1},
                new CryptoContext(new byte[]{1}, java.util.Map.of())))
                .isInstanceOf(SecretConfigurationException.class).hasMessageContaining("AAD");
        assertThatThrownBy(() -> transport.unwrap("key", new byte[]{1},
                new CryptoContext(new byte[0], java.util.Map.of("tenant", "orders"))))
                .isInstanceOf(SecretConfigurationException.class).hasMessageContaining("AAD");
        assertThatThrownBy(() -> transport.wrap("key", new byte[]{1}, CryptoContext.empty()))
                .isInstanceOf(SecretCryptoException.class).hasMessageContaining("wrap failed");
        assertThatThrownBy(() -> transport.unwrap("key", new byte[]{1}, CryptoContext.empty()))
                .isInstanceOf(SecretCryptoException.class).hasMessageContaining("unwrap failed");
    }

    @Test
    void rejectsNonAccessKeyAuthenticationBeforeCreatingBaiduSdkClient() {
        RemoteProviderProperties properties = new RemoteProviderProperties();
        properties.setEndpoint(java.net.URI.create("https://baidu.example"));
        properties.getAuthentication().setType(RemoteProviderProperties.AuthenticationType.NONE);

        assertThatThrownBy(() -> new BaiduSdkSecretTransport(properties))
                .isInstanceOf(SecretConfigurationException.class).hasMessageContaining("access-key");
    }
}
