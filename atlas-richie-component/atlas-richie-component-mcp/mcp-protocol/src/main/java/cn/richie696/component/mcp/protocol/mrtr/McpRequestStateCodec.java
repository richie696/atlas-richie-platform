package cn.richie696.component.mcp.protocol.mrtr;

import cn.richie696.component.mcp.protocol.McpProtocolException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/** Signed opaque MRTR state; payload itself is intentionally not interpreted. */
public final class McpRequestStateCodec {
    private final byte[] secret;

    public McpRequestStateCodec(byte[] secret) {
        if (secret == null || secret.length < 32) {
            throw new IllegalArgumentException("MRTR state secret must contain at least 32 bytes");
        }
        this.secret = secret.clone();
    }

    public String protect(String payload, String principalFingerprint, String method, Instant expiresAt) {
        McpRequestState state = new McpRequestState(
                payload, principalFingerprint, method, expiresAt, UUID.randomUUID().toString());
        String body = encode(state);
        return body + "." + base64(sign(body));
    }

    public McpRequestState verify(String token, String principalFingerprint, String method) {
        try {
            int separator = token.lastIndexOf('.');
            if (separator <= 0) throw invalid();
            String body = token.substring(0, separator);
            byte[] supplied = Base64.getUrlDecoder().decode(token.substring(separator + 1));
            if (!java.security.MessageDigest.isEqual(sign(body), supplied)) throw invalid();
            String[] fields = new String(Base64.getUrlDecoder().decode(body), StandardCharsets.UTF_8).split("\\u0000", -1);
            if (fields.length != 5) throw invalid();
            McpRequestState state = new McpRequestState(
                    fields[0], fields[1], fields[2], Instant.ofEpochMilli(Long.parseLong(fields[3])), fields[4]);
            if (!state.principalFingerprint().equals(principalFingerprint)
                    || !state.method().equals(method)
                    || !state.expiresAt().isAfter(Instant.now())) {
                throw invalid();
            }
            return state;
        } catch (RuntimeException exception) {
            if (exception instanceof McpProtocolException protocolException) throw protocolException;
            throw invalid();
        }
    }

    private String encode(McpRequestState state) {
        String body = String.join("\u0000", state.payload(), state.principalFingerprint(),
                state.method(), Long.toString(state.expiresAt().toEpochMilli()), state.nonce());
        return base64(body.getBytes(StandardCharsets.UTF_8));
    }

    private String base64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to initialize MRTR HMAC", exception);
        }
    }

    private McpProtocolException invalid() {
        return new McpProtocolException("MCP_INVALID_REQUEST_STATE", -32602,
                "Invalid or expired MCP request state", java.util.Map.of());
    }
}
