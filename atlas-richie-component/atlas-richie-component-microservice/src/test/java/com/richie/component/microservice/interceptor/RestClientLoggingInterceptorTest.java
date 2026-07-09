/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.microservice.interceptor;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RestClientLoggingInterceptorTest {

    private final RestClientLoggingInterceptor interceptor = new RestClientLoggingInterceptor();

    @Test
    void intercept_executesRequestAndReturnsWrappedResponse() throws Exception {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.POST, "http://localhost/api");
        request.getHeaders().add("Authorization", "Bearer secret-token-value");
        byte[] body = "{\"name\":\"test\"}".getBytes();
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        MockClientHttpResponse rawResponse = new MockClientHttpResponse("ok".getBytes(), HttpStatus.OK);
        when(execution.execute(any(), any())).thenReturn(rawResponse);

        ClientHttpResponse response = interceptor.intercept(request, body, execution);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(response.getBody().readAllBytes())).isEqualTo("ok");
    }

    @Test
    void intercept_secondCallSkipsDetailedLogging() throws Exception {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, "http://localhost/dedupe");
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        MockClientHttpResponse rawResponse = new MockClientHttpResponse("ok".getBytes(), HttpStatus.OK);
        when(execution.execute(any(), any())).thenReturn(rawResponse);

        interceptor.intercept(request, new byte[0], execution);
        ClientHttpResponse second = interceptor.intercept(request, new byte[0], execution);

        assertThat(second).isSameAs(rawResponse);
    }

    @Test
    void intercept_propagatesExecutionFailure() throws Exception {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.POST, "http://localhost/fail");
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        when(execution.execute(any(), any())).thenThrow(new IOException("boom"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                interceptor.intercept(request, "{\"x\":1}".getBytes(), execution))
                .isInstanceOf(IOException.class);
    }
}
