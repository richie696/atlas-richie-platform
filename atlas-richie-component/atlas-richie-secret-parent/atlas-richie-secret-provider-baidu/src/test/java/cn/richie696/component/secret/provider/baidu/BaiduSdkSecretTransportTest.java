package cn.richie696.component.secret.provider.baidu;

import cn.richie696.component.secret.api.crypto.CryptoContext;
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
}
