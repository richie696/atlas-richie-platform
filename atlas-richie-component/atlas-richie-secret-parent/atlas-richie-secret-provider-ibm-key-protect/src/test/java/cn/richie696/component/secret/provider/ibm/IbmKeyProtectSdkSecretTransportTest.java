package cn.richie696.component.secret.provider.ibm;

import cn.richie696.component.secret.api.crypto.CryptoContext;
import com.ibm.cloud.ibm_key_protect_api.v2.IbmKeyProtectApi;
import com.ibm.cloud.ibm_key_protect_api.v2.model.UnwrapKeyOptions;
import com.ibm.cloud.ibm_key_protect_api.v2.model.UnwrapKeyResponseBody;
import com.ibm.cloud.ibm_key_protect_api.v2.model.WrapKeyOptions;
import com.ibm.cloud.ibm_key_protect_api.v2.model.WrapKeyResponseBody;
import com.ibm.cloud.sdk.core.http.Response;
import com.ibm.cloud.sdk.core.http.ServiceCall;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IbmKeyProtectSdkSecretTransportTest {
    @Test
    @SuppressWarnings("unchecked")
    void mapsInstanceKeyRingAndBase64PayloadThroughTheOfficialSdk() throws Exception {
        IbmKeyProtectApi api = mock(IbmKeyProtectApi.class);
        ServiceCall<WrapKeyResponseBody> wrapCall = mock(ServiceCall.class);
        Response<WrapKeyResponseBody> wrapResponse = mock(Response.class);
        WrapKeyResponseBody wrapBody = mock(WrapKeyResponseBody.class);
        ServiceCall<UnwrapKeyResponseBody> unwrapCall = mock(ServiceCall.class);
        Response<UnwrapKeyResponseBody> unwrapResponse = mock(Response.class);
        UnwrapKeyResponseBody unwrapBody = mock(UnwrapKeyResponseBody.class);
        when(api.wrapKey(any())).thenReturn(wrapCall); when(wrapCall.execute()).thenReturn(wrapResponse);
        when(wrapResponse.getResult()).thenReturn(wrapBody); when(wrapBody.getCiphertext()).thenReturn("ciphertext");
        when(api.unwrapKey(any())).thenReturn(unwrapCall); when(unwrapCall.execute()).thenReturn(unwrapResponse);
        when(unwrapResponse.getResult()).thenReturn(unwrapBody); when(unwrapBody.getPlaintext()).thenReturn("ZGF0YS1rZXk=");
        IbmKeyProtectSdkSecretTransport transport = new IbmKeyProtectSdkSecretTransport(api, "instance-id", "orders");

        byte[] wrapped = transport.wrap("key-id", "data-key".getBytes(StandardCharsets.UTF_8), CryptoContext.empty());
        byte[] unwrapped = transport.unwrap("key-id", wrapped, CryptoContext.empty());

        ArgumentCaptor<WrapKeyOptions> wrap = ArgumentCaptor.forClass(WrapKeyOptions.class);
        ArgumentCaptor<UnwrapKeyOptions> unwrap = ArgumentCaptor.forClass(UnwrapKeyOptions.class);
        verify(api).wrapKey(wrap.capture()); verify(api).unwrapKey(unwrap.capture());
        assertThat(wrap.getValue().id()).isEqualTo("key-id");
        assertThat(wrap.getValue().bluemixInstance()).isEqualTo("instance-id");
        assertThat(wrap.getValue().xKmsKeyRing()).isEqualTo("orders");
        assertThat(new String(wrap.getValue().keyActionWrapBody().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("{\"plaintext\":\"ZGF0YS1rZXk=\"}");
        assertThat(unwrap.getValue().xKmsKeyRing()).isEqualTo("orders");
        assertThat(new String(unwrap.getValue().keyActionUnwrapBody().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("{\"ciphertext\":\"ciphertext\"}");
        assertThat(unwrapped).isEqualTo("data-key".getBytes(StandardCharsets.UTF_8));
    }
}
