package cn.richie696.component.secret.provider.gcp;

import cn.richie696.component.secret.api.SecretVersionSelector;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import com.google.cloud.kms.v1.DecryptRequest;
import com.google.cloud.kms.v1.DecryptResponse;
import com.google.cloud.kms.v1.EncryptRequest;
import com.google.cloud.kms.v1.EncryptResponse;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GcpSdkSecretTransportTest {
    @Test
    void mapsProjectVersionAndAadThroughTheOfficialSdkRequests() {
        SecretManagerServiceClient secrets = mock(SecretManagerServiceClient.class);
        KeyManagementServiceClient kms = mock(KeyManagementServiceClient.class);
        when(secrets.accessSecretVersion(any(String.class))).thenReturn(AccessSecretVersionResponse.newBuilder()
                .setPayload(SecretPayload.newBuilder().setData(ByteString.copyFromUtf8("secret-value"))).build());
        when(kms.encrypt(any(EncryptRequest.class))).thenReturn(EncryptResponse.newBuilder()
                .setCiphertext(ByteString.copyFromUtf8("wrapped")).build());
        when(kms.decrypt(any(DecryptRequest.class))).thenReturn(DecryptResponse.newBuilder()
                .setPlaintext(ByteString.copyFromUtf8("data-key")).build());
        GcpSdkSecretTransport transport = new GcpSdkSecretTransport("project-a", secrets, kms);
        CryptoContext context = new CryptoContext("orders:42".getBytes(StandardCharsets.UTF_8), Map.of());

        var secret = transport.read("database", SecretVersionSelector.version("7"));
        byte[] wrapped = transport.wrap("projects/project-a/locations/global/keyRings/r/cryptoKeys/k",
                "data-key".getBytes(StandardCharsets.UTF_8), context);
        byte[] unwrapped = transport.unwrap("projects/project-a/locations/global/keyRings/r/cryptoKeys/k", wrapped, context);
        transport.close();

        ArgumentCaptor<String> secretName = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<EncryptRequest> encrypt = ArgumentCaptor.forClass(EncryptRequest.class);
        ArgumentCaptor<DecryptRequest> decrypt = ArgumentCaptor.forClass(DecryptRequest.class);
        verify(secrets).accessSecretVersion(secretName.capture());
        verify(kms).encrypt(encrypt.capture());
        verify(kms).decrypt(decrypt.capture());
        verify(secrets).close();
        verify(kms).close();
        assertThat(secret.value()).isEqualTo("secret-value".getBytes(StandardCharsets.UTF_8));
        assertThat(secretName.getValue()).isEqualTo("projects/project-a/secrets/database/versions/7");
        assertThat(encrypt.getValue().getAdditionalAuthenticatedData()).isEqualTo(ByteString.copyFromUtf8("orders:42"));
        assertThat(decrypt.getValue().getAdditionalAuthenticatedData()).isEqualTo(ByteString.copyFromUtf8("orders:42"));
        assertThat(unwrapped).isEqualTo("data-key".getBytes(StandardCharsets.UTF_8));
    }
}
