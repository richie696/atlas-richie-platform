package cn.richie696.component.secret.provider.vault;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class VaultRequestIdCaptureTest {

    @Test
    void attachesAndConsumesAComponentCorrelationId() throws Exception {
        VaultRequestIdCapture capture = new VaultRequestIdCapture();
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://vault/v1/secret"));
        MockClientHttpResponse response = new MockClientHttpResponse(new byte[0], HttpStatus.OK);
        response.getHeaders().add("X-Vault-Request", "true");

        capture.clear();
        capture.interceptor().intercept(request, new byte[0], (ignoredRequest, ignoredBody) -> response);

        String correlationId = request.getHeaders().getFirst(VaultRequestIdCapture.CORRELATION_HEADER);
        assertThat(correlationId).isNotBlank();
        assertThat(capture.consume()).isEqualTo(correlationId);
        assertThat(capture.consume()).isNull();
    }
}
