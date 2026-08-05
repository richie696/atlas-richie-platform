package cn.richie696.component.mcp.transport.http;

import cn.richie696.component.mcp.protocol.McpMetaKeys;
import cn.richie696.component.mcp.protocol.McpProtocolException;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import cn.richie696.component.mcp.protocol.model.McpJsonRpcRequest;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpStreamableHttpRequestValidatorTest {
    private final McpStreamableHttpRequestValidator validator =
            new McpStreamableHttpRequestValidator(
                    Set.of(McpProtocolVersions.V_2026_07_28),
                    origin -> origin.equals("https://foundry.example"));

    @Test
    void validatesModernPostCaseInsensitiveHeadersAndBase64Name() {
        Map<String, List<String>> headers = validHeaders();
        headers.put("origin", List.of("https://foundry.example"));
        headers.put("Mcp-Method", List.of("tools/call"));
        headers.put("mcp-name", List.of("=?base64?5a6i5oi3LmNoZWNrdXA=?="));

        McpValidatedHttpRequest result = validator.validate(request(
                "POST",
                headers,
                "tools/call",
                Map.of("name", "客户.checkup", "arguments", Map.of())));

        assertThat(result.protocolVersion()).isEqualTo(McpProtocolVersions.V_2026_07_28);
        assertThat(result.message().method()).isEqualTo("tools/call");
    }

    @Test
    void rejectsInvalidOriginWithForbidden() {
        Map<String, List<String>> headers = validHeaders();
        headers.put("Origin", List.of("https://attacker.example"));

        assertTransportError(request(
                "POST", headers, "tools/list", Map.of()), 403, null);
    }

    @Test
    void rejectsNonPostUnsupportedMediaTypeAndIncompleteAccept() {
        assertTransportError(request(
                "GET", validHeaders(), "tools/list", Map.of()), 405, null);

        Map<String, List<String>> wrongContentType = validHeaders();
        wrongContentType.put("Content-Type", List.of("text/plain"));
        assertTransportError(request(
                "POST", wrongContentType, "tools/list", Map.of()), 415, null);

        Map<String, List<String>> incompleteAccept = validHeaders();
        incompleteAccept.put("Accept", List.of("application/json"));
        assertTransportError(request(
                "POST", incompleteAccept, "tools/list", Map.of()), 406, null);
    }

    @Test
    void headerVersionMustExistAndMatchBody() {
        Map<String, List<String>> missing = validHeaders();
        missing.remove("MCP-Protocol-Version");
        assertTransportError(request(
                "POST", missing, "tools/list", Map.of()), 400, -32020);

        Map<String, List<String>> mismatch = validHeaders();
        mismatch.put("MCP-Protocol-Version", List.of("2025-11-25"));
        assertTransportError(request(
                "POST", mismatch, "tools/list", Map.of()), 400, -32020);
    }

    @Test
    void rejectsUnsupportedButMatchingProtocolVersion() {
        Map<String, List<String>> headers = validHeaders();
        headers.put("MCP-Protocol-Version", List.of("2027-01-01"));
        McpHttpRequest request = request(
                "POST",
                headers,
                "tools/list",
                Map.of(),
                "2027-01-01");

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOfSatisfying(McpHttpTransportException.class, exception -> {
                    assertThat(exception.httpStatus()).isEqualTo(400);
                    McpProtocolException protocol = exception.protocolError().orElseThrow();
                    assertThat(protocol.jsonRpcCode()).isEqualTo(-32022);
                    assertThat(protocol.data())
                            .containsEntry("requested", "2027-01-01")
                            .containsKey("supported");
                });
    }

    @Test
    void mirroredMethodAndNameMustMatchBody() {
        Map<String, List<String>> wrongMethod = validHeaders();
        wrongMethod.put("Mcp-Method", List.of("resources/read"));
        assertTransportError(request(
                "POST", wrongMethod, "tools/list", Map.of()), 400, -32020);

        Map<String, List<String>> wrongName = validHeaders();
        wrongName.put("Mcp-Method", List.of("tools/call"));
        wrongName.put("Mcp-Name", List.of("other"));
        assertTransportError(request(
                "POST",
                wrongName,
                "tools/call",
                Map.of("name", "expected", "arguments", Map.of())),
                400,
                -32020);
    }

    @Test
    void malformedJsonRpcIsAnHttp400JsonRpcError() {
        McpHttpRequest invalid = new McpHttpRequest(
                "POST",
                validHeaders(),
                new McpJsonRpcRequest("1.0", 1, "tools/list", metadata(Map.of())));

        assertTransportError(invalid, 400, -32600);
    }

    @Test
    void duplicateSecurityHeaderIsRejected() {
        Map<String, List<String>> headers = validHeaders();
        headers.put("mcp-protocol-version", List.of(McpProtocolVersions.V_2026_07_28));

        assertTransportError(request(
                "POST", headers, "tools/list", Map.of()), 400, -32020);
    }

    @Test
    void rejectsBase64NameThatIsNotValidUtf8() {
        Map<String, List<String>> headers = validHeaders();
        headers.put("Mcp-Method", List.of("tools/call"));
        headers.put("Mcp-Name", List.of("=?base64?/w==?="));

        assertTransportError(request(
                "POST",
                headers,
                "tools/call",
                Map.of("name", "expected", "arguments", Map.of())),
                400,
                -32020);
    }

    private McpHttpRequest request(
            String method,
            Map<String, List<String>> headers,
            String rpcMethod,
            Map<String, Object> params) {
        return request(
                method,
                headers,
                rpcMethod,
                params,
                McpProtocolVersions.V_2026_07_28);
    }

    private McpHttpRequest request(
            String method,
            Map<String, List<String>> headers,
            String rpcMethod,
            Map<String, Object> params,
            String bodyVersion) {
        return new McpHttpRequest(
                method,
                headers,
                new McpJsonRpcRequest(
                        "2.0",
                        1,
                        rpcMethod,
                        metadata(params, bodyVersion)));
    }

    private Map<String, Object> metadata(Map<String, Object> params) {
        return metadata(params, McpProtocolVersions.V_2026_07_28);
    }

    private Map<String, Object> metadata(Map<String, Object> params, String version) {
        Map<String, Object> result = new LinkedHashMap<>(params);
        result.put("_meta", Map.of(
                McpMetaKeys.PROTOCOL_VERSION, version,
                McpMetaKeys.CLIENT_CAPABILITIES, Map.of()));
        return result;
    }

    private Map<String, List<String>> validHeaders() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Content-Type", List.of("application/json; charset=utf-8"));
        headers.put("Accept", List.of("application/json, text/event-stream"));
        headers.put("MCP-Protocol-Version", List.of(McpProtocolVersions.V_2026_07_28));
        headers.put("Mcp-Method", List.of("tools/list"));
        return headers;
    }

    private void assertTransportError(
            McpHttpRequest request,
            int status,
            Integer jsonRpcCode) {
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOfSatisfying(McpHttpTransportException.class, exception -> {
                    assertThat(exception.httpStatus()).isEqualTo(status);
                    if (jsonRpcCode == null) {
                        assertThat(exception.protocolError()).isEmpty();
                    } else {
                        assertThat(exception.protocolError().orElseThrow().jsonRpcCode())
                                .isEqualTo(jsonRpcCode);
                    }
                });
    }
}
