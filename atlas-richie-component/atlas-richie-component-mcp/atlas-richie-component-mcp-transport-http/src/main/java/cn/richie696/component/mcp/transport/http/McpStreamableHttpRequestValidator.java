package cn.richie696.component.mcp.transport.http;

import cn.richie696.component.mcp.protocol.McpJsonRpcValidator;
import cn.richie696.component.mcp.protocol.McpMetaKeys;
import cn.richie696.component.mcp.protocol.McpProtocolException;
import cn.richie696.component.mcp.protocol.model.McpJsonRpcRequest;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 2026-07-28 Streamable HTTP POST 与镜像 Header 验证。
 */
public final class McpStreamableHttpRequestValidator {
    private static final String JSON = "application/json";
    private static final String SSE = "text/event-stream";
    private static final String BASE64_PREFIX = "=?base64?";
    private static final String BASE64_SUFFIX = "?=";

    private final Set<String> supportedVersions;
    private final McpOriginPolicy originPolicy;

    public McpStreamableHttpRequestValidator(
            Set<String> supportedVersions,
            McpOriginPolicy originPolicy) {
        if (supportedVersions == null || supportedVersions.isEmpty()) {
            throw new IllegalArgumentException("supportedVersions must not be empty");
        }
        this.supportedVersions = Set.copyOf(supportedVersions);
        this.originPolicy = Objects.requireNonNull(originPolicy, "originPolicy");
    }

    public McpValidatedHttpRequest validate(McpHttpRequest request) {
        Objects.requireNonNull(request, "request");
        if (!"POST".equalsIgnoreCase(request.httpMethod())) {
            throw transportError(405, "MCP endpoint only accepts POST");
        }
        HeaderBag headers = new HeaderBag(request.headers());
        validateOrigin(headers);
        requireMediaType(headers, McpHttpHeaders.CONTENT_TYPE, JSON, 415);
        requireAccept(headers);

        McpJsonRpcRequest message = request.message();
        try {
            McpJsonRpcValidator.validate(message);
        } catch (McpProtocolException exception) {
            throw new McpHttpTransportException(400, exception.getMessage(), exception);
        }
        String bodyVersion = bodyProtocolVersion(message);
        String headerVersion = requireSingle(headers, McpHttpHeaders.PROTOCOL_VERSION);
        requireEqual(McpHttpHeaders.PROTOCOL_VERSION, headerVersion, bodyVersion);
        if (!supportedVersions.contains(headerVersion)) {
            throw unsupportedVersion(headerVersion);
        }

        String headerMethod = requireSingle(headers, McpHttpHeaders.METHOD);
        requireEqual(McpHttpHeaders.METHOD, headerMethod, message.method());
        validateName(headers, message);
        return new McpValidatedHttpRequest(headerVersion, message);
    }

    private void validateOrigin(HeaderBag headers) {
        List<String> origins = headers.values(McpHttpHeaders.ORIGIN);
        if (origins.isEmpty()) {
            return;
        }
        if (origins.size() != 1 || origins.getFirst().isBlank()
                || !originPolicy.isAllowed(origins.getFirst())) {
            throw transportError(403, "Origin is not allowed");
        }
    }

    private void requireAccept(HeaderBag headers) {
        List<String> accepted = commaSeparated(headers.values(McpHttpHeaders.ACCEPT));
        boolean json = accepted.stream().anyMatch(value -> mediaType(value).equals(JSON));
        boolean sse = accepted.stream().anyMatch(value -> mediaType(value).equals(SSE));
        if (!json || !sse) {
            throw transportError(
                    406,
                    "Accept must include application/json and text/event-stream");
        }
    }

    private void requireMediaType(
            HeaderBag headers,
            String name,
            String expected,
            int status) {
        String value = requireSingleTransport(headers, name, status);
        if (!mediaType(value).equals(expected)) {
            throw transportError(status, name + " must be " + expected);
        }
    }

    private String bodyProtocolVersion(McpJsonRpcRequest message) {
        Object metaValue = message.params().get("_meta");
        if (!(metaValue instanceof Map<?, ?> meta)) {
            throw headerMismatch("Request body is missing params._meta");
        }
        Object version = meta.get(McpMetaKeys.PROTOCOL_VERSION);
        if (!(version instanceof String text) || text.isBlank()) {
            throw headerMismatch("Request body is missing protocol version metadata");
        }
        return text;
    }

    private void validateName(HeaderBag headers, McpJsonRpcRequest message) {
        String sourceField = switch (message.method()) {
            case "tools/call", "prompts/get" -> "name";
            case "resources/read" -> "uri";
            default -> null;
        };
        if (sourceField == null) {
            return;
        }
        Object bodyName = message.params().get(sourceField);
        if (!(bodyName instanceof String expected) || expected.isBlank()) {
            throw headerMismatch("Request body is missing params." + sourceField);
        }
        String encoded = requireSingle(headers, McpHttpHeaders.NAME);
        String decoded = decodeHeaderValue(McpHttpHeaders.NAME, encoded);
        requireEqual(McpHttpHeaders.NAME, decoded, expected);
    }

    private String requireSingle(HeaderBag headers, String name) {
        List<String> values = headers.values(name);
        if (values.size() != 1 || values.getFirst().isBlank()) {
            throw headerMismatch(name + " must occur exactly once and must not be blank");
        }
        return values.getFirst();
    }

    private String requireSingleTransport(HeaderBag headers, String name, int status) {
        List<String> values = headers.values(name);
        if (values.size() != 1 || values.getFirst().isBlank()) {
            throw transportError(status, name + " must occur exactly once");
        }
        return values.getFirst();
    }

    private void requireEqual(String name, String headerValue, String bodyValue) {
        if (!headerValue.equals(bodyValue)) {
            throw headerMismatch(name + " does not match the request body");
        }
    }

    private String decodeHeaderValue(String name, String value) {
        if (!matchesBase64Sentinel(value)) {
            if (!safePlainHeader(value)) {
                throw headerMismatch(name + " contains invalid characters");
            }
            return value;
        }
        String payload = value.substring(
                BASE64_PREFIX.length(),
                value.length() - BASE64_SUFFIX.length());
        try {
            byte[] decoded = Base64.getDecoder().decode(payload);
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(decoded))
                    .toString();
        } catch (IllegalArgumentException | CharacterCodingException exception) {
            throw headerMismatch(name + " contains malformed Base64");
        }
    }

    private boolean safePlainHeader(String value) {
        if (!value.equals(value.strip()) || matchesBase64Sentinel(value)) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if ((character < 0x20 && character != '\t') || character > 0x7e) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesBase64Sentinel(String value) {
        return value.startsWith(BASE64_PREFIX) && value.endsWith(BASE64_SUFFIX);
    }

    private List<String> commaSeparated(List<String> values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            for (String part : value.split(",")) {
                if (!part.isBlank()) {
                    result.add(part.strip());
                }
            }
        }
        return result;
    }

    private String mediaType(String value) {
        int parameters = value.indexOf(';');
        return (parameters < 0 ? value : value.substring(0, parameters))
                .strip()
                .toLowerCase(Locale.ROOT);
    }

    private McpHttpTransportException unsupportedVersion(String requested) {
        McpProtocolException error = new McpProtocolException(
                "MCP_UNSUPPORTED_PROTOCOL_VERSION",
                -32022,
                "Unsupported protocol version",
                Map.of(
                        "supported", supportedVersions.stream().sorted().toList(),
                        "requested", requested));
        return new McpHttpTransportException(400, error.getMessage(), error);
    }

    private McpHttpTransportException headerMismatch(String message) {
        McpProtocolException error = new McpProtocolException(
                "MCP_HEADER_MISMATCH",
                -32020,
                "Header mismatch: " + message,
                Map.of());
        return new McpHttpTransportException(400, error.getMessage(), error);
    }

    private McpHttpTransportException transportError(int status, String message) {
        return new McpHttpTransportException(status, message, null);
    }

    private static final class HeaderBag {
        private final Map<String, List<String>> values = new LinkedHashMap<>();

        private HeaderBag(Map<String, List<String>> headers) {
            headers.forEach((name, headerValues) -> values
                    .computeIfAbsent(name.toLowerCase(Locale.ROOT), ignored -> new ArrayList<>())
                    .addAll(headerValues));
        }

        private List<String> values(String name) {
            return List.copyOf(values.getOrDefault(
                    name.toLowerCase(Locale.ROOT),
                    List.of()));
        }
    }
}
