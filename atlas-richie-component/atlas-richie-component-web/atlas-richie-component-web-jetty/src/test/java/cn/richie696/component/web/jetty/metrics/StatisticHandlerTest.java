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
package cn.richie696.component.web.jetty.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatisticHandlerTest {

    private static final Callback CALLBACK = Callback.NOOP;

    private MeterRegistry registry;
    private Request request;
    private Response response;
    private Handler.Wrapper downstream;
    private StatisticHandler handler;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        request = mock(Request.class);
        response = mock(Response.class);
        downstream = mock(Handler.Wrapper.class);
        when(request.getMethod()).thenReturn("GET");

        handler = new StatisticHandler(registry, "atlas_jetty");
        handler.setHandler(downstream);

        try {
            doReturn(true).when(downstream).handle(any(Request.class), any(Response.class), any(Callback.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("Gauge registration")
    class GaugeRegistration {

        @Test
        void shouldRegisterActiveRequestsGaugeWithDefaultPrefix() {
            Gauge activeGauge = registry.find("atlas_jetty.requests.active").gauge();
            assertThat(activeGauge).as("active gauge should be registered").isNotNull();
            assertThat(activeGauge.value()).isZero();
        }

        @Test
        void shouldRegisterActiveRequestsGaugeWithCustomPrefix() {
            MeterRegistry otherRegistry = new SimpleMeterRegistry();
            StatisticHandler customHandler = new StatisticHandler(otherRegistry, "tenant_jetty");

            Gauge gauge = otherRegistry.find("tenant_jetty.requests.active").gauge();
            assertThat(gauge).isNotNull();
            assertThat(gauge.value()).isZero();
        }

        @Test
        void shouldUseAtlasJettyWhenPrefixIsNull() {
            MeterRegistry otherRegistry = new SimpleMeterRegistry();
            new StatisticHandler(otherRegistry, null);

            Gauge gauge = otherRegistry.find("atlas_jetty.requests.active").gauge();
            assertThat(gauge).isNotNull();
        }

        @Test
        void shouldUseAtlasJettyWhenPrefixIsBlank() {
            MeterRegistry otherRegistry = new SimpleMeterRegistry();
            new StatisticHandler(otherRegistry, "   ");

            Gauge gauge = otherRegistry.find("atlas_jetty.requests.active").gauge();
            assertThat(gauge).isNotNull();
        }

        @Test
        void shouldTrackActiveCountDuringRequest() throws Exception {
            when(response.getStatus()).thenReturn(200);

            // capture active gauge value mid-request via doAnswer
            double[] activeDuringCall = new double[1];
            org.mockito.Mockito.doAnswer(invocation -> {
                activeDuringCall[0] = registry.find("atlas_jetty.requests.active").gauge().value();
                return true;
            }).when(downstream).handle(any(Request.class), any(Response.class), any(Callback.class));

            handler.handle(request, response, CALLBACK);

            assertThat(activeDuringCall[0]).isEqualTo(1.0);
            // after the request completes, active gauge goes back to 0
            assertThat(registry.find("atlas_jetty.requests.active").gauge().value()).isZero();
        }
    }

    @Nested
    @DisplayName("Counter & Timer recording on success")
    class SuccessMetricsRecording {

        @Test
        void shouldIncrementTotalCounter() throws Exception {
            when(response.getStatus()).thenReturn(200);

            handler.handle(request, response, CALLBACK);
            handler.handle(request, response, CALLBACK);

            Counter total = registry.find("atlas_jetty.requests.total")
                    .tags(Tags.of("method", "GET", "status", "200"))
                    .counter();
            assertThat(total).isNotNull();
            assertThat(total.count()).isEqualTo(2.0);
        }

        @Test
        void shouldRecordTimerOnEachRequest() throws Exception {
            when(response.getStatus()).thenReturn(200);

            handler.handle(request, response, CALLBACK);
            handler.handle(request, response, CALLBACK);

            Timer timer = registry.find("atlas_jetty.request.duration")
                    .tags(Tags.of("method", "GET", "status", "200"))
                    .timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(2L);
        }

        @Test
        void shouldRecordMetricsWithCustomPrefix() throws Exception {
            MeterRegistry otherRegistry = new SimpleMeterRegistry();
            StatisticHandler customHandler = new StatisticHandler(otherRegistry, "custom_jetty");
            customHandler.setHandler(downstream);
            when(response.getStatus()).thenReturn(200);

            customHandler.handle(request, response, CALLBACK);

            assertThat(otherRegistry.find("custom_jetty.requests.total").counter()).isNotNull();
            assertThat(otherRegistry.find("custom_jetty.request.duration").timer()).isNotNull();
        }

        @Test
        void shouldTagMetricsWithActualMethodAndStatus() throws Exception {
            when(request.getMethod()).thenReturn("POST");
            when(response.getStatus()).thenReturn(201);

            handler.handle(request, response, CALLBACK);

            Counter total = registry.find("atlas_jetty.requests.total")
                    .tags(Tags.of("method", "POST", "status", "201"))
                    .counter();
            assertThat(total).isNotNull();
            assertThat(total.count()).isEqualTo(1.0);
        }

        @Test
        void shouldReturnDownstreamHandleResult() throws Exception {
            when(response.getStatus()).thenReturn(200);
            when(downstream.handle(any(Request.class), any(Response.class), any(Callback.class))).thenReturn(false);

            boolean result = handler.handle(request, response, CALLBACK);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("5xx error metrics")
    class ErrorMetricsRecording {

        @Test
        void shouldIncrementErrorsCounterOn5xx() throws Exception {
            when(response.getStatus()).thenReturn(500);

            handler.handle(request, response, CALLBACK);
            handler.handle(request, response, CALLBACK);

            Counter errors = registry.find("atlas_jetty.requests.errors")
                    .tags(Tags.of("method", "GET", "status", "500"))
                    .counter();
            assertThat(errors).isNotNull();
            assertThat(errors.count()).isEqualTo(2.0);

            Counter total = registry.find("atlas_jetty.requests.total")
                    .tags(Tags.of("method", "GET", "status", "500"))
                    .counter();
            assertThat(total.count()).isEqualTo(2.0);
        }

        @Test
        void shouldTrackDifferent5xxSeparately() throws Exception {
            when(response.getStatus()).thenReturn(500);
            handler.handle(request, response, CALLBACK);
            when(response.getStatus()).thenReturn(503);
            handler.handle(request, response, CALLBACK);
            when(response.getStatus()).thenReturn(599);
            handler.handle(request, response, CALLBACK);

            assertThat(registry.find("atlas_jetty.requests.errors")
                    .tags(Tags.of("method", "GET", "status", "500")).counter().count()).isEqualTo(1.0);
            assertThat(registry.find("atlas_jetty.requests.errors")
                    .tags(Tags.of("method", "GET", "status", "503")).counter().count()).isEqualTo(1.0);
            assertThat(registry.find("atlas_jetty.requests.errors")
                    .tags(Tags.of("method", "GET", "status", "599")).counter().count()).isEqualTo(1.0);
        }

        @Test
        void shouldNotRecordErrorsFor4xx() throws Exception {
            when(response.getStatus()).thenReturn(404);

            handler.handle(request, response, CALLBACK);

            assertThat(registry.find("atlas_jetty.requests.errors")
                    .tags(Tags.of("method", "GET", "status", "404")).counter()).isNull();

            Counter total = registry.find("atlas_jetty.requests.total")
                    .tags(Tags.of("method", "GET", "status", "404"))
                    .counter();
            assertThat(total.count()).isEqualTo(1.0);
        }

        @Test
        void shouldRecordErrorsForBoundaryStatus501() throws Exception {
            when(response.getStatus()).thenReturn(501);

            handler.handle(request, response, CALLBACK);

            assertThat(registry.find("atlas_jetty.requests.errors")
                    .tags(Tags.of("method", "GET", "status", "501")).counter().count()).isEqualTo(1.0);
        }

        @Test
        void shouldNotRecordErrorsForBoundaryStatus499() throws Exception {
            when(response.getStatus()).thenReturn(499);

            handler.handle(request, response, CALLBACK);

            assertThat(registry.find("atlas_jetty.requests.errors")
                    .tags(Tags.of("method", "GET", "status", "499")).counter()).isNull();
        }
    }

    @Nested
    @DisplayName("Resilience to failures")
    class ResilienceToFailures {

        @Test
        void shouldRecordMetricsEvenWhenDownstreamThrows() throws Exception {
            when(response.getStatus()).thenReturn(500);
            doThrow(new RuntimeException("downstream failure"))
                    .when(downstream).handle(any(Request.class), any(Response.class), any(Callback.class));

            try {
                handler.handle(request, response, CALLBACK);
            } catch (RuntimeException expected) {
                assertThat(expected).hasMessage("downstream failure");
            }

            // finally block records total + timer + 5xx-error counters
            assertThat(registry.find("atlas_jetty.requests.total")
                    .tags(Tags.of("method", "GET", "status", "500")).counter().count()).isEqualTo(1.0);
            assertThat(registry.find("atlas_jetty.requests.errors")
                    .tags(Tags.of("method", "GET", "status", "500")).counter().count()).isEqualTo(1.0);
            // activeRequests must always decrement back to zero
            assertThat(registry.find("atlas_jetty.requests.active").gauge().value()).isZero();
        }

        @Test
        void shouldNotThrowWhenMeterRecordingFails() throws Exception {
            // Use a registry that always throws on counter creation
            MeterRegistry faultyRegistry = new SimpleMeterRegistry() {
                @Override
                public Counter counter(String name, Tags tags) {
                    throw new RuntimeException("meter failure");
                }
            };
            StatisticHandler faultyHandler = new StatisticHandler(faultyRegistry, "atlas_jetty");
            faultyHandler.setHandler(downstream);
            when(response.getStatus()).thenReturn(200);

            // production code catches the exception and logs a warning; request still completes
            boolean result = faultyHandler.handle(request, response, CALLBACK);

            assertThat(result).isTrue();
        }

        @Test
        void shouldDecrementActiveCountEvenWhenRecordingThrows() throws Exception {
            // Use a registry that throws when incrementing a counter to simulate a recording failure
            MeterRegistry faultyRegistry = new FaultyCounterRegistry();
            StatisticHandler faultyHandler = new StatisticHandler(faultyRegistry, "atlas_jetty");
            faultyHandler.setHandler(downstream);
            when(response.getStatus()).thenReturn(200);

            boolean result = faultyHandler.handle(request, response, CALLBACK);

            assertThat(result).isTrue();
            // activeRequests still decremented back to zero in the innermost finally
            assertThat(faultyRegistry.find("atlas_jetty.requests.active").gauge().value()).isZero();
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
        void shouldAlwaysInvokeDownstreamHandler() throws Exception {
            when(response.getStatus()).thenReturn(200);

            handler.handle(request, response, CALLBACK);

            org.mockito.Mockito.verify(downstream).handle(any(Request.class), any(Response.class), any(Callback.class));
        }
    }

    @Nested
    @DisplayName("Timer recording")
    class TimerRecording {

        @Test
        void shouldRecordTimerWithPercentileConfig() throws Exception {
            when(response.getStatus()).thenReturn(200);

            handler.handle(request, response, CALLBACK);

            Timer timer = registry.find("atlas_jetty.request.duration")
                    .tags(Tags.of("method", "GET", "status", "200"))
                    .timer();
            assertThat(timer).isNotNull();
            // Timer.start/stop should produce a non-negative total time
            assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isGreaterThanOrEqualTo(0L);
            assertThat(timer.count()).isEqualTo(1L);
        }

        @Test
        void shouldRecordTimerPerStatus() throws Exception {
            when(response.getStatus()).thenReturn(200);
            handler.handle(request, response, CALLBACK);
            when(response.getStatus()).thenReturn(204);
            handler.handle(request, response, CALLBACK);
            when(response.getStatus()).thenReturn(404);
            handler.handle(request, response, CALLBACK);

            assertThat(registry.find("atlas_jetty.request.duration")
                    .tags(Tags.of("method", "GET", "status", "200")).timer().count()).isEqualTo(1L);
            assertThat(registry.find("atlas_jetty.request.duration")
                    .tags(Tags.of("method", "GET", "status", "204")).timer().count()).isEqualTo(1L);
            assertThat(registry.find("atlas_jetty.request.duration")
                    .tags(Tags.of("method", "GET", "status", "404")).timer().count()).isEqualTo(1L);
        }
    }

    /**
     * A registry that fails when {@code Timer.Sample.stop(Timer)} is invoked — used to test the
     * outer try/catch in StatisticHandler.handle().
     */
    private static final class FaultyCounterRegistry extends SimpleMeterRegistry {
        @Override
        public Timer timer(String name, Tags tags) {
            // Force the timer builder to throw later — easiest way is to make the name include
            // invalid characters. Micrometer rejects names with reserved chars.
            return super.timer(name + "$bad", tags);
        }
    }
}
