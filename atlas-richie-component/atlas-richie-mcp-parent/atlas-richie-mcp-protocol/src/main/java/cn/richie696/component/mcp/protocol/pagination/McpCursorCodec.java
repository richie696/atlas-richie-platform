package cn.richie696.component.mcp.protocol.pagination;

import cn.richie696.component.mcp.protocol.McpProtocolException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * 服务端分页游标的编解码器：对外表现为不透明字符串，完整性受 HMAC 保护。
 *
 * <p>为什么需要这个 codec：传统 {@code page=1&size=20} 形式的分页 URL 会暴露业务分页
 * 状态、且无任何防篡改保护。本类用 HMAC-SHA256 对分页偏移量进行签名，使游标对外
 * 表现为不透明字符串，攻击者无法猜测或篡改偏移量。</p>
 *
 * 关键安全设计：
 * <ul>
 *   <li>签名使用 {@link java.security.MessageDigest#isEqual} 常量时间比较，避免时序攻击。</li>
 *   <li>secret 至少 16 字节（构造期强校验）。</li>
 *   <li>{@link #decode} 会对游标做严格 round-trip 校验：解码后的字节流必须能再次
 *       生成完全相同的输入字符串，防止攻击者通过构造变形 Base64 序列绕过签名。</li>
 *   <li>空/空白游标视为 {@code offset=0}（首页的便捷约定）。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpCursorCodec {
    private static final String HMAC = "HmacSHA256";
    private final byte[] secret;

    /**
     * 构造分页游标编解码器。
     *
     * @param secret HMAC 密钥，至少 16 字节
     * @throws IllegalArgumentException 当密钥为 {@code null} 或短于 16 字节时
     */
    public McpCursorCodec(byte[] secret) {
        if (secret == null || secret.length < 16) {
            throw new IllegalArgumentException("cursor secret must contain at least 16 bytes");
        }
        this.secret = secret.clone();
    }

    /**
     * 把分页偏移量编码为不透明游标。
     *
     * @param offset 非负偏移量
     * @return 游标字符串
     * @throws IllegalArgumentException 当 {@code offset} 为负时
     */
    public String encode(int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }
        String payload = Integer.toString(offset);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload)))
                        .getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 从不透明游标中解码出分页偏移量。
     *
     * <p>空/空白游标被解读为 {@code 0}（首页）。任何格式错误、签名不匹配、round-trip
     * 校验失败的情况都视为无效游标，统一抛 {@link McpProtocolException}。</p>
     *
     * @param cursor 游标字符串，可为空
     * @return 偏移量
     * @throws McpProtocolException 当游标格式错误、签名不匹配或 round-trip 校验失败时
     */
    public int decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            byte[] encoded = Base64.getUrlDecoder().decode(cursor);
            if (!Base64.getUrlEncoder().withoutPadding().encodeToString(encoded).equals(cursor)) {
                // 严格 round-trip：解码后必须能再次生成完全相同的字符串
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
