package cn.richie696.component.mcp.transport.http;

import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import cn.richie696.component.mcp.protocol.model.McpImplementationInfo;
import cn.richie696.component.mcp.server.tool.McpToolRegistry;
import cn.richie696.component.mcp.server.tool.McpToolVisibilityPolicy;
import cn.richie696.component.observability.core.DependencyMetricsRecorder;
import cn.richie696.component.observability.core.ObservabilityContext;
import cn.richie696.component.observability.core.ObservabilityState;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class McpObservabilityTest {

    private OpenTelemetrySdk sdk;

    @AfterEach
    void tearDown() {
        if (sdk != null) {
            sdk.close();
        }
    }

    @Test
    void clientInjectsW3cAndRecordsMcpDependency() throws Exception {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        sdk = sdk(exporter);
        RecordingMetrics metrics = new RecordingMetrics();
        HttpClient httpClient = mock(HttpClient.class);
        HttpRequest[] captured = new HttpRequest[1];
        doAnswer(invocation -> {
            captured[0] = invocation.getArgument(0);
            return response(200, "{\"jsonrpc\":\"2.0\",\"id\":\"x\",\"result\":{"
                    + "\"resultType\":\"complete\",\"supportedVersions\":[\"2026-07-28\"],\"capabilities\":{},"
                    + "\"ttlMs\":1000,\"cacheScope\":\"public\"}}");
        }).when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        McpHttpToolClient client = new McpHttpToolClient(
                httpClient, Duration.ofSeconds(5), "client", "1.0",
                McpProtocolVersions.V_2026_07_28, 10, 100,
                sdk, metrics, ObservabilityState.enabledState());
        Span parent = sdk.getTracer("test").spanBuilder("parent").startSpan();
        try (Scope ignored = parent.makeCurrent();
             Scope request = ObservabilityContext.withRequestId("mcp-request")) {
            client.discover(URI.create("https://mcp.example.com/mcp"), Map.of());
        }
        parent.end();

        assertThat(captured[0].headers().firstValue("traceparent")).isPresent();
        assertThat(captured[0].headers().firstValue("x-request-id")).contains("mcp-request");
        var clientSpan = exporter.getFinishedSpanItems().stream()
                .filter(span -> span.getKind() == SpanKind.CLIENT)
                .findFirst().orElseThrow();
        assertThat(clientSpan.getName()).isEqualTo("MCP server/discover");
        assertThat(clientSpan.getSpanContext().getTraceId()).isEqualTo(parent.getSpanContext().getTraceId());
        assertThat(metrics.requests.get()).isEqualTo(1);
    }

    @Test
    void serverExtractsW3cAndKeepsRequestIdSeparate() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        sdk = sdk(exporter);
        RecordingMetrics metrics = new RecordingMetrics();
        McpServerHttpEndpoint endpoint = new McpServerHttpEndpoint(
                new McpToolRegistry(McpToolVisibilityPolicy.ALLOW_ALL),
                new McpImplementationInfo("mcp-server", "1.0"),
                origin -> true,
                new cn.richie696.component.mcp.server.resource.McpResourceRegistry(),
                new cn.richie696.component.mcp.server.prompt.McpPromptRegistry(),
                null, List.of(),
                cn.richie696.component.mcp.api.server.McpCallContextFactory.anonymous(),
                sdk, metrics, ObservabilityState.enabledState());

        Span parent = sdk.getTracer("test").spanBuilder("parent").startSpan();
        Map<String, List<String>> headers = validHeaders("ping");
        sdk.getPropagators().getTextMapPropagator().inject(
                parent.storeInContext(io.opentelemetry.context.Context.current()), headers,
                (carrier, key, value) -> carrier.put(key, List.of(value)));
        headers.put("x-request-id", List.of("mcp-request"));

        McpHttpResponse response = endpoint.handle(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\",\"params\":{"
                        + "\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\","
                        + "\"io.modelcontextprotocol/clientCapabilities\":{}}}}",
                headers);
        parent.end();

        assertThat(response.status()).as("body=%s", response.body()).isEqualTo(200);
        var serverSpan = exporter.getFinishedSpanItems().stream()
                .filter(span -> span.getKind() == SpanKind.SERVER)
                .findFirst().orElseThrow();
        assertThat(serverSpan.getName()).isEqualTo("MCP ping");
        assertThat(serverSpan.getSpanContext().getTraceId()).isEqualTo(parent.getSpanContext().getTraceId());
        assertThat(serverSpan.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("request_id")))
                .isEqualTo("mcp-request");
        assertThat(metrics.requests.get()).isEqualTo(1);
    }

    private OpenTelemetrySdk sdk(InMemorySpanExporter exporter) {
        return OpenTelemetrySdk.builder()
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build())
                .build();
    }

    private static HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        doAnswer(invocation -> status).when(response).statusCode();
        doAnswer(invocation -> body).when(response).body();
        doAnswer(invocation -> HttpHeaders.of(Map.of(), (key, value) -> true)).when(response).headers();
        return response;
    }

    private static Map<String, List<String>> validHeaders(String method) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Content-Type", List.of("application/json"));
        headers.put("Accept", List.of("application/json, text/event-stream"));
        headers.put("MCP-Protocol-Version", List.of(McpProtocolVersions.V_2026_07_28));
        headers.put("Mcp-Method", List.of(method));
        return headers;
    }

    private static final class RecordingMetrics implements DependencyMetricsRecorder {
        private final AtomicInteger requests = new AtomicInteger();

        @Override
        public void recordRequest(String dependencyType, String targetService, String operation,
                                  String status, long durationNanos) {
            requests.incrementAndGet();
        }

        @Override
        public void recordConnection(String dependencyType, String targetService, long activeConnections) {
        }
    }
}
