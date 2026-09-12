package cn.richie696.component.secret.provider.huawei;

import cn.richie696.component.secret.api.SecretVersionSelector;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.exception.SecretException;
import com.huaweicloud.sdk.core.exception.ServiceResponseException;
import com.huaweicloud.sdk.csms.v1.CsmsClient;
import com.huaweicloud.sdk.csms.v1.model.ShowSecretVersionRequest;
import com.huaweicloud.sdk.csms.v1.model.ShowSecretVersionResponse;
import com.huaweicloud.sdk.csms.v1.model.Version;
import com.huaweicloud.sdk.csms.v1.model.VersionMetadata;
import com.huaweicloud.sdk.kms.v2.KmsClient;
import com.huaweicloud.sdk.kms.v2.model.DecryptDataRequest;
import com.huaweicloud.sdk.kms.v2.model.DecryptDataResponse;
import com.huaweicloud.sdk.kms.v2.model.EncryptDataRequest;
import com.huaweicloud.sdk.kms.v2.model.EncryptDataResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HuaweiSdkSecretTransportTest {
    @Test
    void mapsCsmsVersionsAndKmsAadThroughTheOfficialSdkModels() {
        CsmsClient csms = mock(CsmsClient.class);
        KmsClient kms = mock(KmsClient.class);
        HuaweiSdkSecretTransport transport = new HuaweiSdkSecretTransport(csms, kms);
        when(csms.showSecretVersion(any())).thenReturn(new ShowSecretVersionResponse().withVersion(
                new Version().withSecretString("secret-value").withVersionMetadata(
                        new VersionMetadata().withId("v7").withCreateTime(1_700_000_000_000L))));
        when(kms.encryptData(any())).thenReturn(new EncryptDataResponse().withCipherText("ciphertext"));
        when(kms.decryptData(any())).thenReturn(new DecryptDataResponse()
                .withPlainTextBase64(Base64.getEncoder().encodeToString("data-key".getBytes(StandardCharsets.UTF_8))));

        var secret = transport.read("database", SecretVersionSelector.version("v7"));
        byte[] plaintext = "data-key".getBytes(StandardCharsets.UTF_8);
        CryptoContext context = new CryptoContext("orders:42".getBytes(StandardCharsets.UTF_8), Map.of());
        byte[] wrapped = transport.wrap("cmk-id", plaintext, context);
        byte[] unwrapped = transport.unwrap("cmk-id", wrapped, context);

        ArgumentCaptor<ShowSecretVersionRequest> secretRequest = ArgumentCaptor.forClass(ShowSecretVersionRequest.class);
        ArgumentCaptor<EncryptDataRequest> encryptRequest = ArgumentCaptor.forClass(EncryptDataRequest.class);
        ArgumentCaptor<DecryptDataRequest> decryptRequest = ArgumentCaptor.forClass(DecryptDataRequest.class);
        org.mockito.Mockito.verify(csms).showSecretVersion(secretRequest.capture());
        org.mockito.Mockito.verify(kms).encryptData(encryptRequest.capture());
        org.mockito.Mockito.verify(kms).decryptData(decryptRequest.capture());

        assertThat(secret.value()).isEqualTo("secret-value");
        assertThat(secret.version()).isEqualTo("v7");
        assertThat(secret.createdAt()).isEqualTo(Instant.ofEpochMilli(1_700_000_000_000L));
        assertThat(secretRequest.getValue().getSecretName()).isEqualTo("database");
        assertThat(secretRequest.getValue().getVersionId()).isEqualTo("v7");
        assertThat(encryptRequest.getValue().getBody().getKeyId()).isEqualTo("cmk-id");
        assertThat(encryptRequest.getValue().getBody().getPlainText())
                .isEqualTo(Base64.getEncoder().encodeToString(plaintext));
        assertThat(encryptRequest.getValue().getBody().getAdditionalAuthenticatedData())
                .isEqualTo(Base64.getEncoder().encodeToString("orders:42".getBytes(StandardCharsets.UTF_8)));
        assertThat(decryptRequest.getValue().getBody().getCipherText()).isEqualTo("ciphertext");
        assertThat(decryptRequest.getValue().getBody().getAdditionalAuthenticatedData())
                .isEqualTo(encryptRequest.getValue().getBody().getAdditionalAuthenticatedData());
        assertThat(unwrapped).isEqualTo(plaintext);
    }

    @Test
    void mapsOnlyNotFoundToAMissingSecret() {
        CsmsClient csms = mock(CsmsClient.class);
        when(csms.showSecretVersion(any())).thenThrow(new ServiceResponseException(404, "NOT_FOUND", "missing", "request-1"));
        HuaweiSdkSecretTransport transport = new HuaweiSdkSecretTransport(csms, mock(KmsClient.class));

        assertThat(transport.read("missing", SecretVersionSelector.latest())).isNull();

        doThrow(new ServiceResponseException(403, "FORBIDDEN", "denied", "request-2"))
                .when(csms).showSecretVersion(any());
        assertThatThrownBy(() -> transport.read("forbidden", SecretVersionSelector.latest()))
                .isInstanceOf(SecretException.class)
                .hasMessageContaining("CSMS read failed");
    }
}
