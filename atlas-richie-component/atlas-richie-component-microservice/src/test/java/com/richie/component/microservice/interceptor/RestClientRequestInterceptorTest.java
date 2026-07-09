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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RestClientRequestInterceptorTest {

    private final RestClientRequestInterceptor interceptor = new RestClientRequestInterceptor();

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void intercept_withoutRequestContext_delegatesToExecution() throws Exception {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, "http://localhost/api");
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        ClientHttpResponse response = mock(ClientHttpResponse.class);
        when(execution.execute(any(), any())).thenReturn(response);

        assertThat(interceptor.intercept(request, new byte[0], execution)).isSameAs(response);
    }

    @Test
    void intercept_forwardsCustomHeaderAndRemovesContentTypeOnGet() throws Exception {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader("X-Trace-Id", "trace-1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));

        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, "http://localhost/api");
        request.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        ClientHttpResponse response = mock(ClientHttpResponse.class);
        when(execution.execute(any(), any())).thenReturn(response);

        interceptor.intercept(request, new byte[0], execution);

        assertThat(request.getHeaders().getFirst("X-Trace-Id")).isEqualTo("trace-1");
        assertThat(request.getHeaders().containsHeader(HttpHeaders.CONTENT_TYPE)).isFalse();
    }

    @Test
    void intercept_nonGetRequest_keepsContentType() throws Exception {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader("X-Trace-Id", "trace-2");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));

        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.POST, "http://localhost/api");
        request.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        ClientHttpResponse response = mock(ClientHttpResponse.class);
        when(execution.execute(any(), any())).thenReturn(response);

        interceptor.intercept(request, new byte[0], execution);

        assertThat(request.getHeaders().containsHeader(HttpHeaders.CONTENT_TYPE)).isTrue();
    }

    @Test
    void init_doesNotThrow() {
        interceptor.init();
    }
}
