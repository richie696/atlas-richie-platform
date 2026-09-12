package cn.richie696.component.secret.provider.azure;

import cn.richie696.component.secret.api.SecretVersionSelector;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import com.azure.security.keyvault.keys.cryptography.CryptographyClient;
import com.azure.security.keyvault.keys.cryptography.models.KeyWrapAlgorithm;
import com.azure.security.keyvault.keys.cryptography.models.UnwrapResult;
import com.azure.security.keyvault.keys.cryptography.models.WrapResult;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AzureSdkSecretTransportTest {
    @Test
    void mapsVersionedSecretAndRsaWrapOperationsThroughAzureSdk() {
        SecretClient secrets = mock(SecretClient.class);
        CryptographyClient crypto = mock(CryptographyClient.class);
        when(secrets.getSecret("database", "v7")).thenReturn(new KeyVaultSecret("database", "secret-value"));
        when(crypto.wrapKey(any(), any())).thenReturn(new WrapResult("wrapped".getBytes(StandardCharsets.UTF_8),
                KeyWrapAlgorithm.RSA_OAEP_256, "key-id"));
        when(crypto.unwrapKey(any(), any())).thenReturn(new UnwrapResult("data-key".getBytes(StandardCharsets.UTF_8),
                KeyWrapAlgorithm.RSA_OAEP_256, "key-id"));
        AzureSdkSecretTransport transport = new AzureSdkSecretTransport(secrets, ignored -> crypto);

        var secret = transport.read("database", SecretVersionSelector.version("v7"));
        byte[] wrapped = transport.wrap("key-id", "data-key".getBytes(StandardCharsets.UTF_8), CryptoContext.empty());
        byte[] unwrapped = transport.unwrap("key-id", wrapped, CryptoContext.empty());

        ArgumentCaptor<KeyWrapAlgorithm> algorithm = ArgumentCaptor.forClass(KeyWrapAlgorithm.class);
        verify(crypto).wrapKey(algorithm.capture(), any());
        verify(crypto).unwrapKey(KeyWrapAlgorithm.RSA_OAEP_256, "wrapped".getBytes(StandardCharsets.UTF_8));
        assertThat(secret.value()).isEqualTo("secret-value");
        assertThat(algorithm.getValue()).isEqualTo(KeyWrapAlgorithm.RSA_OAEP_256);
        assertThat(unwrapped).isEqualTo("data-key".getBytes(StandardCharsets.UTF_8));
    }
}
