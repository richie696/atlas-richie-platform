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
package cn.richie696.component.web.jetty.handler;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TraceIdInjectHandlerTest {

    private static final Callback CALLBACK = Callback.NOOP;

    private Request request;
    private Response response;
    private HttpFields.Mutable requestHeaders;
    private HttpFields.Mutable responseHeaders;
    private Map<String, Object> requestAttributes;
    private Handler.Wrapper downstream;
    private TraceIdInjectHandler handler;

    @BeforeEach
    void setUp() {
        request = mock(Request.class);
        response = mock(Response.class);
        requestHeaders = HttpFields.build();
        responseHeaders = HttpFields.build();
        requestAttributes = new HashMap<>();
        downstream = mock(Handler.Wrapper.class);

        when(request.getHeaders()).thenReturn(requestHeaders);
        when(response.getHeaders()).thenReturn(responseHeaders);
        // Request.setAttribute stores into the local map; getAttribute reads back from it
        when(request.getAttribute(any(String.class)))
                .thenAnswer(invocation -> requestAttributes.get(invocation.getArgument(0, String.class)));
        doAnswer(invocation -> {
            requestAttributes.put(invocation.getArgument(0, String.class), invocation.getArgument(1));
            return null;
        }).when(request).setAttribute(any(String.class), any());

        handler = new TraceIdInjectHandler("X-Trace-Id", true);
        handler.setHandler(downstream);

        // Default downstream behaviour: return true (handled)
        try {
            doReturn(true).when(downstream).handle(any(Request.class), any(Response.class), any(Callback.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Nested
    @DisplayName("Trace ID extraction from request header")
    class TraceIdExtraction {

        @Test
        void shouldUseProvidedTraceIdFromHeader() throws Exception {
            requestHeaders.put("X-Trace-Id", "client-trace-abc");

            boolean result = handler.handle(request, response, CALLBACK);

            assertThat(result).isTrue();
            assertThat(capturedTraceId()).isEqualTo("client-trace-abc");
            assertThat(MDC.get(TraceIdInjectHandler.TRACE_ID_MDC_KEY)).isEqualTo("client-trace-abc");
            assertThat(responseHeaders.get("X-Trace-Id")).isEqualTo("client-trace-abc");
            verify(downstream).handle(eq(request), eq(response), eq(CALLBACK));
        }

        @Test
        void shouldGenerateUuidWhenHeaderMissingAndGenerateIfMissingTrue() throws Exception {
            // no header on request
            boolean result = handler.handle(request, response, CALLBACK);

            assertThat(result).isTrue();
            String traceId = capturedTraceId();
            assertThat(traceId).isNotNull();
            // UUID format: 8-4-4-4-12 hex chars
            assertThat(traceId).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            assertThat(MDC.get(TraceIdInjectHandler.TRACE_ID_MDC_KEY)).isEqualTo(traceId);
            assertThat(responseHeaders.get("X-Trace-Id")).isEqualTo(traceId);
        }

        @Test
        void shouldUseNaFallbackWhenHeaderMissingAndGenerateIfMissingFalse() throws Exception {
            handler = new TraceIdInjectHandler("X-Trace-Id", false);
            handler.setHandler(downstream);

            boolean result = handler.handle(request, response, CALLBACK);

            assertThat(result).isTrue();
            assertThat(capturedTraceId()).isEqualTo("n/a");
            assertThat(MDC.get(TraceIdInjectHandler.TRACE_ID_MDC_KEY)).isEqualTo("n/a");
            assertThat(responseHeaders.get("X-Trace-Id")).isEqualTo("n/a");
        }

        @Test
        void shouldTreatBlankHeaderAsMissing() throws Exception {
            requestHeaders.put("X-Trace-Id", "   ");

            boolean result = handler.handle(request, response, CALLBACK);

            assertThat(result).isTrue();
            String traceId = capturedTraceId();
            assertThat(traceId).isNotEqualTo("   ");
            assertThat(traceId).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            assertThat(responseHeaders.get("X-Trace-Id")).isEqualTo(traceId);
        }

        @Test
        void shouldPropagateDownstreamReturnValue() throws Exception {
            when(downstream.handle(request, response, CALLBACK)).thenReturn(false);

            boolean result = handler.handle(request, response, CALLBACK);

            assertThat(result).isFalse();
            // trace id was still injected before delegating
            assertThat(capturedTraceId()).isNotNull();
        }

        @Test
        void shouldStillWriteTraceIdWhenDownstreamThrows() throws Exception {
            doThrow(new RuntimeException("downstream boom"))
                    .when(downstream).handle(any(Request.class), any(Response.class), any(Callback.class));

            try {
                handler.handle(request, response, CALLBACK);
            } catch (RuntimeException expected) {
                assertThat(expected).hasMessage("downstream boom");
            }

            // trace id must have been injected before the delegation
            assertThat(capturedTraceId()).isNotNull();
            assertThat(responseHeaders.get("X-Trace-Id")).isNotNull();
        }
    }

    @Nested
    @DisplayName("Header name resolution")
    class HeaderNameResolution {

        @Test
        void shouldDefaultWhenHeaderNameIsNull() throws Exception {
            handler = new TraceIdInjectHandler(null, true);
            handler.setHandler(downstream);

            handler.handle(request, response, CALLBACK);

            assertThat(responseHeaders.get(TraceIdInjectHandler.DEFAULT_HEADER)).isNotNull();
        }

        @Test
        void shouldDefaultWhenHeaderNameIsBlank() throws Exception {
            handler = new TraceIdInjectHandler("   ", true);
            handler.setHandler(downstream);

            handler.handle(request, response, CALLBACK);

            assertThat(responseHeaders.get(TraceIdInjectHandler.DEFAULT_HEADER)).isNotNull();
        }

        @Test
        void shouldUseCustomHeaderNameWhenProvided() throws Exception {
            handler = new TraceIdInjectHandler("X-Custom-Trace", true);
            handler.setHandler(downstream);
            requestHeaders.put("X-Custom-Trace", "from-custom-header");

            handler.handle(request, response, CALLBACK);

            assertThat(capturedTraceId()).isEqualTo("from-custom-header");
            assertThat(responseHeaders.get("X-Custom-Trace")).isEqualTo("from-custom-header");
        }
    }

    @Nested
    @DisplayName("Side effects on Request / Response")
    class SideEffects {

        @Test
        void shouldPopulateRequestAttributeExactlyOnce() throws Exception {
            requestHeaders.put("X-Trace-Id", "first-trace");

            handler.handle(request, response, CALLBACK);

            ArgumentCaptor<String> nameCap = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object> valueCap = ArgumentCaptor.forClass(Object.class);
            verify(request).setAttribute(nameCap.capture(), valueCap.capture());
            assertThat(nameCap.getValue()).isEqualTo(TraceIdInjectHandler.TRACE_ID_REQUEST_ATTR);
            assertThat(valueCap.getValue()).isEqualTo("first-trace");
        }

        @Test
        void shouldWriteMdcEvenWhenHeaderIsProvided() throws Exception {
            requestHeaders.put("X-Trace-Id", "header-trace-id");

            handler.handle(request, response, CALLBACK);

            assertThat(MDC.get(TraceIdInjectHandler.TRACE_ID_MDC_KEY)).isEqualTo("header-trace-id");
        }

        @Test
        void shouldWriteResponseHeaderWhenHeaderIsBlankAndGenerateDisabled() throws Exception {
            handler = new TraceIdInjectHandler("X-Trace-Id", false);
            handler.setHandler(downstream);

            handler.handle(request, response, CALLBACK);

            assertThat(responseHeaders.get("X-Trace-Id")).isEqualTo("n/a");
        }

        @Test
        void shouldWriteGeneratedUuidIntoResponseHeader() throws Exception {
            handler.handle(request, response, CALLBACK);

            String headerValue = responseHeaders.get("X-Trace-Id");
            assertThat(headerValue).isNotNull();
            assertThat(headerValue).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }
    }

    @Nested
    @DisplayName("Delegation behaviour")
    class DelegationBehaviour {

        @Test
        void shouldAlwaysInvokeDownstreamHandler() throws Exception {
            handler.handle(request, response, CALLBACK);

            verify(downstream).handle(eq(request), eq(response), eq(CALLBACK));
        }

        @Test
        void shouldRecordTraceIdBeforeDelegating() throws Exception {
            // capture the request attribute at the moment downstream is called
            doAnswer(invocation -> {
                Object captured = ((Request) invocation.getArgument(0))
                        .getAttribute(TraceIdInjectHandler.TRACE_ID_REQUEST_ATTR);
                assertThat(captured).as("trace id must be set BEFORE delegation").isNotNull();
                return true;
            }).when(downstream).handle(any(Request.class), any(Response.class), any(Callback.class));

            handler.handle(request, response, CALLBACK);

            assertThat(capturedTraceId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Handler chain wiring")
    class HandlerChainWiring {

        @Test
        void shouldExposeTheWrappedHandler() {
            assertThat(handler.getHandler()).isSameAs(downstream);
        }

        @Test
        void shouldSupportOverridingTheWrappedHandler() throws Exception {
            Handler second = mock(Handler.Wrapper.class);
            HttpFields.Mutable reqHeaders2 = HttpFields.build();
            HttpFields.Mutable resHeaders2 = HttpFields.build();
            Map<String, Object> attrs2 = new HashMap<>();
            Request req2 = mock(Request.class);
            Response res2 = mock(Response.class);
            when(req2.getHeaders()).thenReturn(reqHeaders2);
            when(res2.getHeaders()).thenReturn(resHeaders2);
            when(req2.getAttribute(any(String.class))).thenAnswer(invocation -> attrs2.get(invocation.getArgument(0, String.class)));
            doAnswer(invocation -> {
                attrs2.put(invocation.getArgument(0, String.class), invocation.getArgument(1));
                return null;
            }).when(req2).setAttribute(any(String.class), any());
            doReturn(true).when(second).handle(any(Request.class), any(Response.class), any(Callback.class));

            handler.setHandler(second);
            handler.handle(req2, res2, CALLBACK);

            verify(second).handle(eq(req2), eq(res2), eq(CALLBACK));
            verify(downstream, org.mockito.Mockito.never()).handle(any(), any(), any());
        }
    }

    private String capturedTraceId() {
        return (String) requestAttributes.get(TraceIdInjectHandler.TRACE_ID_REQUEST_ATTR);
    }
}
