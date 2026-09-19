package cn.richie696.component.secret.provider.tencent;

import cn.richie696.component.secret.api.SecretVersionSelector;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.exception.SecretCryptoException;
import cn.richie696.component.secret.api.exception.SecretException;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.kms.v20190118.KmsClient;
import com.tencentcloudapi.kms.v20190118.models.DecryptRequest;
import com.tencentcloudapi.kms.v20190118.models.DecryptResponse;
import com.tencentcloudapi.kms.v20190118.models.EncryptRequest;
import com.tencentcloudapi.kms.v20190118.models.EncryptResponse;
import com.tencentcloudapi.ssm.v20190923.SsmClient;
import com.tencentcloudapi.ssm.v20190923.models.GetSecretValueRequest;
import com.tencentcloudapi.ssm.v20190923.models.GetSecretValueResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TencentSdkSecretTransportTest {
    @Test
    void mapsSecretVersionAndCryptoContextThroughSdkModels() throws Exception {
        SsmClient ssm = mock(SsmClient.class);
        KmsClient kms = mock(KmsClient.class);
        GetSecretValueResponse secretResponse = new GetSecretValueResponse();
        secretResponse.setSecretString("secret-value"); secretResponse.setVersionId("v7"); secretResponse.setRequestId("req-1");
        EncryptResponse encryptResponse = new EncryptResponse(); encryptResponse.setCiphertextBlob("ciphertext");
        DecryptResponse decryptResponse = new DecryptResponse(); decryptResponse.setPlaintext("ZGF0YS1rZXk=");
        when(ssm.GetSecretValue(any())).thenReturn(secretResponse);
        when(kms.Encrypt(any())).thenReturn(encryptResponse);
        when(kms.Decrypt(any())).thenReturn(decryptResponse);
        TencentSdkSecretTransport transport = new TencentSdkSecretTransport(ssm, kms);
        CryptoContext context = new CryptoContext("orders:42".getBytes(StandardCharsets.UTF_8), Map.of("tenant", "t1"));

        var secret = transport.read("database", SecretVersionSelector.version("v7"));
        byte[] wrapped = transport.wrap("cmk-id", "data-key".getBytes(StandardCharsets.UTF_8), context);
        byte[] unwrapped = transport.unwrap("cmk-id", wrapped, context);

        ArgumentCaptor<GetSecretValueRequest> read = ArgumentCaptor.forClass(GetSecretValueRequest.class);
        ArgumentCaptor<EncryptRequest> encrypt = ArgumentCaptor.forClass(EncryptRequest.class);
        ArgumentCaptor<DecryptRequest> decrypt = ArgumentCaptor.forClass(DecryptRequest.class);
        verify(ssm).GetSecretValue(read.capture()); verify(kms).Encrypt(encrypt.capture()); verify(kms).Decrypt(decrypt.capture());
        assertThat(secret.value()).isEqualTo("secret-value".getBytes(StandardCharsets.UTF_8));
        assertThat(read.getValue().getSecretName()).isEqualTo("database");
        assertThat(read.getValue().getVersionId()).isEqualTo("v7");
        assertThat(encrypt.getValue().getEncryptionContext()).isEqualTo("atlas.secret.aad=b3JkZXJzOjQy\ntenant=t1");
        assertThat(decrypt.getValue().getEncryptionContext()).isEqualTo(encrypt.getValue().getEncryptionContext());
        assertThat(unwrapped).isEqualTo("data-key".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void supportsLatestBinarySecretsAndMapsSdkFailures() throws Exception {
        SsmClient ssm = mock(SsmClient.class);
        KmsClient kms = mock(KmsClient.class);
        GetSecretValueResponse binary = new GetSecretValueResponse();
        binary.setSecretBinary(java.util.Base64.getEncoder().encodeToString("binary".getBytes(StandardCharsets.UTF_8)));
        binary.setVersionId("current"); binary.setRequestId("req-binary");
        TencentCloudSDKException missing = mock(TencentCloudSDKException.class);
        when(missing.getErrorCode()).thenReturn("ResourceNotFound");
        TencentCloudSDKException broken = mock(TencentCloudSDKException.class);
        when(broken.getErrorCode()).thenReturn("InternalError");
        when(ssm.GetSecretValue(any())).thenReturn(binary).thenThrow(missing).thenThrow(broken);
        TencentSdkSecretTransport transport = new TencentSdkSecretTransport(ssm, kms);

        var secret = transport.read("database", SecretVersionSelector.latest());
        assertThat(secret.value()).isEqualTo("binary".getBytes(StandardCharsets.UTF_8));
        assertThat(transport.read("missing", SecretVersionSelector.latest())).isNull();
        assertThatThrownBy(() -> transport.read("broken", SecretVersionSelector.latest()))
                .isInstanceOf(SecretException.class).hasMessageContaining("read failed");
    }

    @Test
    void mapsSdkCryptoFailuresAndSupportsNullContext() throws Exception {
        SsmClient ssm = mock(SsmClient.class);
        KmsClient kms = mock(KmsClient.class);
        when(kms.Encrypt(any())).thenThrow(mock(TencentCloudSDKException.class));
        when(kms.Decrypt(any())).thenThrow(mock(TencentCloudSDKException.class));
        TencentSdkSecretTransport transport = new TencentSdkSecretTransport(ssm, kms);

        assertThatThrownBy(() -> transport.wrap("key", new byte[]{1}, null))
                .isInstanceOf(SecretCryptoException.class).hasMessageContaining("wrap failed");
        assertThatThrownBy(() -> transport.unwrap("key", new byte[]{1}, null))
                .isInstanceOf(SecretCryptoException.class).hasMessageContaining("unwrap failed");
    }
}
