package cn.richie696.component.mcp.protocol.mrtr;

import cn.richie696.component.mcp.protocol.McpProtocolException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * 签发与验证不可伪造的 MRTR（Multi-Request Token Relay）状态令牌。
 *
 * <p>设计意图：MCP 协议的某些业务（如多步工具调用、跨域恢复）需要把第一阶段的入参、
 * 主体指纹、目标方法、过期时间等绑定在一起传递给第二阶段。本 codec 用 HMAC-SHA256
 * 产生一个对外表现为"不透明字符串"的令牌，服务器可验证签名/过期/主体/方法，但不解析
 * payload 本身（payload 是任意字符串）。</p>
 *
 * <p>关键安全设计：
 * <ul>
 *   <li>签名使用 {@link java.security.MessageDigest#isEqual} 常量时间比较，避免时序攻击。</li>
 *   <li>secret 至少 32 字节（构造期强校验），低于此长度即抛 {@link IllegalArgumentException}。</li>
 *   <li>secret 字段使用防御性拷贝，外部修改不会影响内部签名密钥。</li>
 *   <li>每次 {@link #protect} 调用都生成新的 UUID nonce，防止重放。</li>
 *   <li>{@code principalFingerprint} 与 {@code method} 同时参与校验，防止令牌被其他
 *       会话/方法劫持复用。</li>
 * </ul>
 * </p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpRequestStateCodec {
    private final byte[] secret;

    /**
     * 构造 MRTR 编解码器。
     *
     * @param secret HMAC 密钥，至少 32 字节
     * @throws IllegalArgumentException 当密钥为 {@code null} 或短于 32 字节时
     */
    public McpRequestStateCodec(byte[] secret) {
        if (secret == null || secret.length < 32) {
            throw new IllegalArgumentException("MRTR state secret must contain at least 32 bytes");
        }
        this.secret = secret.clone();
    }

    /**
     * 签发一个 MRTR 状态令牌。
     *
     * @param payload              不透明的负载字符串（业务侧自定，不被本 codec 解析）
     * @param principalFingerprint 主体指纹
     * @param method               目标方法名
     * @param expiresAt            过期时间点
     * @return 形如 {@code "<base64>.<base64>"} 的令牌字符串
     */
    public String protect(String payload, String principalFingerprint, String method, Instant expiresAt) {
        McpRequestState state = new McpRequestState(
                payload, principalFingerprint, method, expiresAt, UUID.randomUUID().toString());
        String body = encode(state);
        return body + "." + base64(sign(body));
    }

    /**
     * 验证一个 MRTR 状态令牌。
     *
     * @param token                待验证的令牌字符串
     * @param principalFingerprint 当前主体指纹（必须与签发时一致）
     * @param method               当前调用方法（必须与签发时一致）
     * @return 解析后的 {@link McpRequestState}
     * @throws McpProtocolException 当令牌格式错误、签名不匹配、主体/方法不一致或已过期时
     */
    public McpRequestState verify(String token, String principalFingerprint, String method) {
        try {
            int separator = token.lastIndexOf('.');
            if (separator <= 0) throw invalid();
            String body = token.substring(0, separator);
            byte[] supplied = Base64.getUrlDecoder().decode(token.substring(separator + 1));
            if (!java.security.MessageDigest.isEqual(sign(body), supplied)) throw invalid();
            String[] fields = new String(Base64.getUrlDecoder().decode(body), StandardCharsets.UTF_8).split("\u0000", -1);
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
