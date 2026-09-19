package cn.richie696.component.secret.provider.oci;

import cn.richie696.component.secret.api.SecretVersionSelector;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.api.exception.SecretCryptoException;
import cn.richie696.component.secret.api.exception.SecretException;
import cn.richie696.component.secret.provider.common.RemoteProviderProperties;
import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.keymanagement.KmsCryptoClient;
import com.oracle.bmc.keymanagement.model.DecryptedData;
import com.oracle.bmc.keymanagement.model.EncryptedData;
import com.oracle.bmc.keymanagement.requests.DecryptRequest;
import com.oracle.bmc.keymanagement.requests.EncryptRequest;
import com.oracle.bmc.keymanagement.responses.DecryptResponse;
import com.oracle.bmc.keymanagement.responses.EncryptResponse;
import com.oracle.bmc.secrets.SecretsClient;
import com.oracle.bmc.secrets.model.Base64SecretBundleContentDetails;
import com.oracle.bmc.secrets.model.SecretBundle;
import com.oracle.bmc.secrets.requests.GetSecretBundleRequest;
import com.oracle.bmc.secrets.responses.GetSecretBundleResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OciSdkSecretTransportTest {
    @Test
    void mapsStageSecretAndAadThroughOciSdkModels() {
        SecretsClient secrets = mock(SecretsClient.class);
        KmsCryptoClient kms = mock(KmsCryptoClient.class);
        SecretBundle bundle = SecretBundle.builder().versionName("v7")
                .secretBundleContent(Base64SecretBundleContentDetails.builder()
                        .content(Base64.getEncoder().encodeToString("secret-value".getBytes(StandardCharsets.UTF_8))).build()).build();
        when(secrets.getSecretBundle(any())).thenReturn(GetSecretBundleResponse.builder().secretBundle(bundle).build());
        when(kms.encrypt(any())).thenReturn(EncryptResponse.builder()
                .encryptedData(EncryptedData.builder().ciphertext("ciphertext").build()).build());
        when(kms.decrypt(any())).thenReturn(DecryptResponse.builder()
                .decryptedData(DecryptedData.builder().plaintext("ZGF0YS1rZXk=").build()).build());
        OciSdkSecretTransport transport = new OciSdkSecretTransport(secrets, kms);
        CryptoContext context = new CryptoContext("orders:42".getBytes(StandardCharsets.UTF_8), Map.of("tenant", "t1"));

        var secret = transport.read("ocid1.secret", SecretVersionSelector.stage("current"));
        byte[] wrapped = transport.wrap("ocid1.key", "data-key".getBytes(StandardCharsets.UTF_8), context);
        byte[] unwrapped = transport.unwrap("ocid1.key", wrapped, context);

        ArgumentCaptor<GetSecretBundleRequest> read = ArgumentCaptor.forClass(GetSecretBundleRequest.class);
        ArgumentCaptor<EncryptRequest> encrypt = ArgumentCaptor.forClass(EncryptRequest.class);
        ArgumentCaptor<DecryptRequest> decrypt = ArgumentCaptor.forClass(DecryptRequest.class);
        verify(secrets).getSecretBundle(read.capture()); verify(kms).encrypt(encrypt.capture()); verify(kms).decrypt(decrypt.capture());
        assertThat(secret.value()).isEqualTo("secret-value".getBytes(StandardCharsets.UTF_8));
        assertThat(read.getValue().getStage()).isEqualTo(GetSecretBundleRequest.Stage.Current);
        assertThat(encrypt.getValue().getEncryptDataDetails().getAssociatedData())
                .containsEntry("atlas.secret.aad", "b3JkZXJzOjQy").containsEntry("tenant", "t1");
        assertThat(decrypt.getValue().getDecryptDataDetails().getAssociatedData())
                .isEqualTo(encrypt.getValue().getEncryptDataDetails().getAssociatedData());
        assertThat(unwrapped).isEqualTo("data-key".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void supportsVersionAliasAndRejectsUnsupportedSecretContent() {
        SecretsClient secrets = mock(SecretsClient.class);
        SecretBundle bundle = SecretBundle.builder().versionName("v8").build();
        when(secrets.getSecretBundle(any())).thenReturn(GetSecretBundleResponse.builder().secretBundle(bundle).build());
        OciSdkSecretTransport transport = new OciSdkSecretTransport(secrets, mock(KmsCryptoClient.class));

        assertThatThrownBy(() -> transport.read("secret", SecretVersionSelector.version("8")))
                .isInstanceOf(SecretException.class).hasMessageContaining("unsupported");
        assertThatThrownBy(() -> transport.read("secret", SecretVersionSelector.alias("current")))
                .isInstanceOf(SecretException.class).hasMessageContaining("unsupported");
    }

    @Test
    void mapsVaultNotFoundAndKmsFailures() {
        SecretsClient secrets = mock(SecretsClient.class);
        KmsCryptoClient kms = mock(KmsCryptoClient.class);
        BmcException missing = mock(BmcException.class);
        when(missing.getStatusCode()).thenReturn(404);
        when(secrets.getSecretBundle(any())).thenThrow(missing);
        when(kms.encrypt(any())).thenThrow(new IllegalStateException("encrypt"));
        when(kms.decrypt(any())).thenThrow(new IllegalStateException("decrypt"));
        OciSdkSecretTransport transport = new OciSdkSecretTransport(secrets, kms);

        assertThat(transport.read("missing", SecretVersionSelector.latest())).isNull();
        assertThatThrownBy(() -> transport.wrap("key", new byte[]{1}, CryptoContext.empty()))
                .isInstanceOf(SecretCryptoException.class).hasMessageContaining("wrap failed");
        assertThatThrownBy(() -> transport.unwrap("key", new byte[]{1}, null))
                .isInstanceOf(SecretCryptoException.class).hasMessageContaining("unwrap failed");
    }

    @Test
    void rejectsUnsupportedPrincipalAuthentication() {
        RemoteProviderProperties properties = new RemoteProviderProperties();
        properties.setEndpoint(java.net.URI.create("https://oci.example"));
        properties.getAuthentication().setType(RemoteProviderProperties.AuthenticationType.BEARER_TOKEN);

        assertThatThrownBy(() -> new OciSdkSecretTransport(properties))
                .isInstanceOf(SecretConfigurationException.class).hasMessageContaining("Instance or Resource Principal");
    }

    @Test
    void constructsSdkClientsForInstancePrincipalConfiguration() {
        RemoteProviderProperties properties = new RemoteProviderProperties();
        properties.setEndpoint(java.net.URI.create("https://oci.example"));
        properties.getAuthentication().setType(RemoteProviderProperties.AuthenticationType.NONE);

        assertThatThrownBy(() -> new OciSdkSecretTransport(properties))
                .isInstanceOf(RuntimeException.class);
    }
}
