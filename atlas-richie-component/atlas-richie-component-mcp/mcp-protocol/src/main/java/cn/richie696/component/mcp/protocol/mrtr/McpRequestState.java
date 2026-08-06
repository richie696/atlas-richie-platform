package cn.richie696.component.mcp.protocol.mrtr;

import java.time.Instant;
import java.util.Objects;

public record McpRequestState(
        String payload,
        String principalFingerprint,
        String method,
        Instant expiresAt,
        String nonce) {
    public McpRequestState {
        if (payload == null || payload.isBlank()) throw new IllegalArgumentException("payload must not be blank");
        if (principalFingerprint == null || principalFingerprint.isBlank()) {
            throw new IllegalArgumentException("principalFingerprint must not be blank");
        }
        if (method == null || method.isBlank()) throw new IllegalArgumentException("method must not be blank");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (nonce == null || nonce.isBlank()) throw new IllegalArgumentException("nonce must not be blank");
    }
}
