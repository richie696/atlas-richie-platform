package cn.richie696.component.mcp.protocol.pagination;

import cn.richie696.component.mcp.protocol.McpProtocolException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/** Opaque, integrity-protected cursor codec for server-side pagination. */
public final class McpCursorCodec {
    private static final String HMAC = "HmacSHA256";
    private final byte[] secret;

    public McpCursorCodec(byte[] secret) {
        if (secret == null || secret.length < 16) {
            throw new IllegalArgumentException("cursor secret must contain at least 16 bytes");
        }
        this.secret = secret.clone();
    }

    public String encode(int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }
        String payload = Integer.toString(offset);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload)))
                        .getBytes(StandardCharsets.UTF_8));
    }

    public int decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            byte[] encoded = Base64.getUrlDecoder().decode(cursor);
            if (!Base64.getUrlEncoder().withoutPadding().encodeToString(encoded).equals(cursor)) {
                throw invalid();
            }
            String value = new String(encoded, StandardCharsets.UTF_8);
            int separator = value.indexOf('.');
            if (separator <= 0 || separator == value.length() - 1) {
                throw invalid();
            }
            String payload = value.substring(0, separator);
            byte[] supplied = Base64.getUrlDecoder().decode(value.substring(separator + 1));
            if (!java.security.MessageDigest.isEqual(sign(payload), supplied)) {
                throw invalid();
            }
            int offset = Integer.parseInt(payload);
            if (offset < 0) {
                throw invalid();
            }
            return offset;
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret, HMAC));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to initialize cursor HMAC", exception);
        }
    }

    private McpProtocolException invalid() {
        return new McpProtocolException(
                "MCP_INVALID_CURSOR", -32602, "Invalid pagination cursor", java.util.Map.of());
    }
}
