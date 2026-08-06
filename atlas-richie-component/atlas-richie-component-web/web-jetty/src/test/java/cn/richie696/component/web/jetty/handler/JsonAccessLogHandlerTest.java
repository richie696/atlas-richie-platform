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
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class JsonAccessLogHandlerTest {

    private static final Callback CALLBACK = Callback.NOOP;

    private List<String> captured;
    private JsonAccessLogHandler.OutputWriter writer;
    private Request request;
    private Response response;
    private HttpFields.Mutable requestHeaders;
    private Handler.Wrapper downstream;
    private JsonAccessLogHandler handler;
    private MockedStatic<Request> requestStaticMock;

    @BeforeEach
    void setUp() {
        captured = new ArrayList<>();
        writer = captured::add;
        request = mock(Request.class);
        response = mock(Response.class);
        requestHeaders = HttpFields.build();
        downstream = mock(Handler.Wrapper.class);

        when(request.getHeaders()).thenReturn(requestHeaders);
        when(request.getHttpURI()).thenReturn(HttpURI.from("/api/users/123"));
        when(request.getMethod()).thenReturn("GET");

        // Mock the static methods that the handler uses for remote address / port
        requestStaticMock = mockStatic(Request.class, Mockito.CALLS_REAL_METHODS);
        requestStaticMock.when(() -> Request.getRemoteAddr(request)).thenReturn("192.168.1.10");
        requestStaticMock.when(() -> Request.getRemotePort(request)).thenReturn(54321);

        handler = new JsonAccessLogHandler(writer);
        handler.setHandler(downstream);

        try {
            doReturn(true).when(downstream).handle(any(Request.class), any(Response.class), any(Callback.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @AfterEach
    void tearDown() {
        if (requestStaticMock != null) {
            requestStaticMock.close();
        }
    }

    @Nested
    @DisplayName("Counters and metadata")
    class CountersAndMetadata {

        @Test
        void shouldIncrementTotalCountOnEachRequest() throws Exception {
            responseSetup(200);
            handler.handle(request, response, CALLBACK);

            assertThat(handler.getTotalCount()).isEqualTo(1L);

            handler.handle(request, response, CALLBACK);
            handler.handle(request, response, CALLBACK);
            assertThat(handler.getTotalCount()).isEqualTo(3L);
        }

        @Test
        void shouldIncrementPerStatusCount() throws Exception {
            responseSetup(200);
            handler.handle(request, response, CALLBACK);
            responseSetup(404);
            handler.handle(request, response, CALLBACK);
            responseSetup(500);
            handler.handle(request, response, CALLBACK);
            responseSetup(200);
            handler.handle(request, response, CALLBACK);

            assertThat(handler.getStatusCount("200")).isEqualTo(2L);
            assertThat(handler.getStatusCount("404")).isEqualTo(1L);
            assertThat(handler.getStatusCount("500")).isEqualTo(1L);
            assertThat(handler.getStatusCount("503")).isEqualTo(0L);
        }

        @Test
        void shouldReturnZeroForMissingStatus() {
            assertThat(handler.getStatusCount("999")).isZero();
        }

        @Test
        void shouldStillCountWhenDownstreamThrows() throws Exception {
            responseSetup(500);
            doThrow(new RuntimeException("downstream failure"))
                    .when(downstream).handle(any(Request.class), any(Response.class), any(Callback.class));

            try {
                handler.handle(request, response, CALLBACK);
            } catch (RuntimeException expected) {
                assertThat(expected).hasMessage("downstream failure");
            }

            // finally block in production code increments the count even on failure
            assertThat(handler.getTotalCount()).isEqualTo(1L);
            assertThat(handler.getStatusCount("500")).isEqualTo(1L);
        }

        @Test
        void shouldReturnZeroOnInitialState() {
            assertThat(handler.getTotalCount()).isZero();
        }
    }

    @Nested
    @DisplayName("JSON output shape")
    class JsonOutputShape {

        @Test
        void shouldEmitOneJsonLinePerRequest() throws Exception {
            responseSetup(200);
            requestHeaders.put("User-Agent", "curl/7.85.0");
            requestHeaders.put("X-Trace-Id", "trace-xyz");

            handler.handle(request, response, CALLBACK);

            assertThat(captured).hasSize(1);
            assertThat(captured.get(0)).startsWith("{").endsWith("}");
        }

        @Test
        void shouldIncludeAllExpectedFields() throws Exception {
            responseSetup(200);
            requestHeaders.put("User-Agent", "curl/7.85.0");
            requestHeaders.put("X-Trace-Id", "trace-xyz");

            handler.handle(request, response, CALLBACK);

            String json = captured.get(0);
            assertThat(json).contains("\"ts\":");
            assertThat(json).contains("\"method\":\"GET\"");
            assertThat(json).contains("\"uri\":\"/api/users/123\"");
            assertThat(json).contains("\"status\":200");
            assertThat(json).contains("\"duration_ms\":");
            assertThat(json).contains("\"remote\":\"192.168.1.10:54321\"");
            assertThat(json).contains("\"ua\":\"curl/7.85.0\"");
            assertThat(json).contains("\"trace_id\":\"trace-xyz\"");
        }

        @Test
        void shouldHandleMissingUserAgentAndTraceId() throws Exception {
            responseSetup(200);
            // do not add User-Agent or X-Trace-Id

            handler.handle(request, response, CALLBACK);

            String json = captured.get(0);
            assertThat(json).contains("\"ua\":null");
            assertThat(json).contains("\"trace_id\":null");
        }

        @Test
        void shouldSerializeDurationMsAsNumber() throws Exception {
            responseSetup(200);

            handler.handle(request, response, CALLBACK);

            // duration_ms value is bounded by elapsed nanos, just ensure it is an unquoted integer-like token
            String json = captured.get(0);
            assertThat(json).matches(".*\"duration_ms\":\\d+.*");
        }

        @Test
        void shouldSerializeStatusAsNumber() throws Exception {
            responseSetup(201);

            handler.handle(request, response, CALLBACK);

            String json = captured.get(0);
            assertThat(json).contains("\"status\":201");
        }
    }

    @Nested
    @DisplayName("JSON escape behaviour")
    class JsonEscapeBehaviour {

        @Test
        void shouldEscapeBackslashInUserAgent() throws Exception {
            responseSetup(200);
            requestHeaders.put("User-Agent", "evil\\agent");

            handler.handle(request, response, CALLBACK);

            assertThat(captured.get(0)).contains("\"ua\":\"evil\\\\agent\"");
        }

        @Test
        void shouldEscapeDoubleQuoteInUserAgent() throws Exception {
            responseSetup(200);
            requestHeaders.put("User-Agent", "name\"with-quote");

            handler.handle(request, response, CALLBACK);

            assertThat(captured.get(0)).contains("\"ua\":\"name\\\"with-quote\"");
        }

        @Test
        void shouldEscapeMixedSpecialChars() throws Exception {
            responseSetup(200);
            requestHeaders.put("X-Trace-Id", "trace\"with\\backslash");

            handler.handle(request, response, CALLBACK);

            assertThat(captured.get(0)).contains("\"trace_id\":\"trace\\\"with\\\\backslash\"");
        }
    }

    @Nested
    @DisplayName("Resilience to writer / handler failures")
    class ResilienceToFailures {

        @Test
        void shouldSwallowWriterExceptions() throws Exception {
            responseSetup(200);
            JsonAccessLogHandler failingHandler = new JsonAccessLogHandler(line -> {
                throw new RuntimeException("writer failure");
            });
            failingHandler.setHandler(downstream);

            // must not throw
            failingHandler.handle(request, response, CALLBACK);

            // total count still incremented inside the try block, even though writer threw
            assertThat(failingHandler.getTotalCount()).isEqualTo(1L);
        }

        @Test
        void shouldStillReturnDownstreamResultWhenWriterFails() throws Exception {
            responseSetup(200);
            JsonAccessLogHandler failingHandler = new JsonAccessLogHandler(line -> {
                throw new RuntimeException("writer failure");
            });
            failingHandler.setHandler(downstream);
            when(downstream.handle(any(Request.class), any(Response.class), any(Callback.class))).thenReturn(false);

            boolean result = failingHandler.handle(request, response, CALLBACK);

            assertThat(result).isFalse();
        }

        @Test
        void shouldReturnDownstreamHandleResult() throws Exception {
            responseSetup(204);
            when(downstream.handle(any(Request.class), any(Response.class), any(Callback.class))).thenReturn(true);

            boolean result = handler.handle(request, response, CALLBACK);

            assertThat(result).isTrue();
        }

        @Test
        void shouldEmitAccessLogEvenWhenDownstreamReturnsFalse() throws Exception {
            responseSetup(404);
            when(downstream.handle(any(Request.class), any(Response.class), any(Callback.class))).thenReturn(false);

            handler.handle(request, response, CALLBACK);

            assertThat(captured).hasSize(1);
            assertThat(captured.get(0)).contains("\"status\":404");
        }
    }

    @Nested
    @DisplayName("OutputWriter interface")
    class OutputWriterContract {

        @Test
        void shouldInvokeWriterForEveryRequest() throws Exception {
            responseSetup(200);

            handler.handle(request, response, CALLBACK);
            handler.handle(request, response, CALLBACK);
            handler.handle(request, response, CALLBACK);

            assertThat(captured).hasSize(3);
        }

        @Test
        void shouldAlwaysEmitJsonEvenWhenResponseStatusIsZero() throws Exception {
            when(response.getStatus()).thenReturn(0);

            handler.handle(request, response, CALLBACK);

            assertThat(captured).hasSize(1);
            assertThat(captured.get(0)).contains("\"status\":0");
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
            responseSetup(200);
            when(second.handle(any(Request.class), any(Response.class), any(Callback.class))).thenReturn(true);

            handler.setHandler(second);
            handler.handle(request, response, CALLBACK);

            Mockito.verify(second).handle(any(Request.class), any(Response.class), any(Callback.class));
            Mockito.verify(downstream, Mockito.never())
                    .handle(any(Request.class), any(Response.class), any(Callback.class));
        }
    }

    private void responseSetup(int status) {
        when(response.getStatus()).thenReturn(status);
    }
}
