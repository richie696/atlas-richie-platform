package cn.richie696.component.secret.provider.huawei;

import cn.richie696.component.secret.api.SecretVersionSelector;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.api.exception.SecretCryptoException;
import cn.richie696.component.secret.api.exception.SecretException;
import cn.richie696.component.secret.provider.common.RemoteProviderProperties;
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
import java.net.URI;
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

    @Test
    void supportsLatestBinarySecretsMissingMetadataAndPlaintextFallback() {
        CsmsClient csms = mock(CsmsClient.class);
        KmsClient kms = mock(KmsClient.class);
        Version binary = new Version().withSecretBinary(Base64.getEncoder()
                .encodeToString("binary".getBytes(StandardCharsets.UTF_8)));
        when(csms.showSecretVersion(any())).thenReturn(new ShowSecretVersionResponse().withVersion(binary));
        when(kms.encryptData(any())).thenReturn(new EncryptDataResponse().withCipherText("cipher"));
        when(kms.decryptData(any())).thenReturn(new DecryptDataResponse().withPlainText("plain"));
        HuaweiSdkSecretTransport transport = new HuaweiSdkSecretTransport(csms, kms);

        var secret = transport.read("binary", SecretVersionSelector.latest());
        assertThat(secret.value()).isEqualTo("binary".getBytes(StandardCharsets.UTF_8));
        assertThat(secret.version()).isNull();
        assertThat(secret.createdAt()).isNull();
        assertThat(transport.unwrap("key", "cipher".getBytes(StandardCharsets.UTF_8), null))
                .isEqualTo("plain".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void mapsMissingVersionAndKmsRuntimeFailures() {
        CsmsClient csms = mock(CsmsClient.class);
        KmsClient kms = mock(KmsClient.class);
        when(csms.showSecretVersion(any())).thenReturn(new ShowSecretVersionResponse());
        when(kms.encryptData(any())).thenThrow(new IllegalStateException("encrypt"));
        when(kms.decryptData(any())).thenThrow(new IllegalStateException("decrypt"));
        HuaweiSdkSecretTransport transport = new HuaweiSdkSecretTransport(csms, kms);

        assertThatThrownBy(() -> transport.read("broken", SecretVersionSelector.latest()))
                .isInstanceOf(SecretException.class).hasMessageContaining("no Secret version");
        assertThatThrownBy(() -> transport.wrap("key", new byte[]{1}, CryptoContext.empty()))
                .isInstanceOf(SecretCryptoException.class).hasMessageContaining("wrap failed");
        assertThatThrownBy(() -> transport.unwrap("key", new byte[]{1}, CryptoContext.empty()))
                .isInstanceOf(SecretCryptoException.class).hasMessageContaining("unwrap failed");
    }

    @Test
    void rejectsUnsupportedAuthenticationBeforeCreatingHuaweiSdkClients() {
        RemoteProviderProperties properties = new RemoteProviderProperties();
        properties.setProjectId("project");
        properties.setEndpoint(URI.create("https://huaweicloud.example"));
        properties.getAuthentication().setType(RemoteProviderProperties.AuthenticationType.NONE);
        assertThatThrownBy(() -> new HuaweiSdkSecretTransport(properties))
                .isInstanceOf(SecretConfigurationException.class).hasMessageContaining("access-key");
    }
}
