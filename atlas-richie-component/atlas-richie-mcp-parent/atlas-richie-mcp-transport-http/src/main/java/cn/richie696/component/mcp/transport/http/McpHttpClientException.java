package cn.richie696.component.mcp.transport.http;

import cn.richie696.component.mcp.protocol.McpProtocolException;

import java.util.Optional;
import java.util.List;
import java.util.Map;

/** Stable client-side failure; wire/library exceptions do not cross the adapter boundary. */
public final class McpHttpClientException extends RuntimeException {
    private final int httpStatus;
    private final McpProtocolException protocolError;
    private final Map<String, List<String>> responseHeaders;

    public McpHttpClientException(String message, int httpStatus, McpProtocolException protocolError, Throwable cause) {
        this(message, httpStatus, protocolError, cause, Map.of());
    }

    public McpHttpClientException(
            String message,
            int httpStatus,
            McpProtocolException protocolError,
            Throwable cause,
            Map<String, List<String>> responseHeaders) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.protocolError = protocolError;
        this.responseHeaders = responseHeaders == null ? Map.of() : Map.copyOf(responseHeaders);
    }

    public int httpStatus() {
        return httpStatus;
    }

    public Optional<McpProtocolException> protocolError() {
        return Optional.ofNullable(protocolError);
    }

    /** HTTP response headers, including WWW-Authenticate on a 401 challenge. */
    public Map<String, List<String>> responseHeaders() {
        return responseHeaders;
    }

    public Optional<String> firstHeader(String name) {
        return responseHeaders.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .flatMap(entry -> entry.getValue().stream())
                .findFirst();
    }
}
