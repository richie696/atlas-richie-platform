package cn.richie696.component.mcp.protocol.mrtr;

import java.time.Instant;
import java.util.Objects;

/**
 * MRTR 状态令牌的解析结果。
 *
 * <p>五个字段的语义：
 * <ul>
 *   <li>{@code payload} —— 不透明负载，本类型不解析其内容。</li>
 *   <li>{@code principalFingerprint} —— 主体指纹，{@link McpRequestStateCodec#verify} 时校验。</li>
 *   <li>{@code method} —— 目标方法名，{@link McpRequestStateCodec#verify} 时校验。</li>
 *   <li>{@code expiresAt} —— 过期时间点。</li>
 *   <li>{@code nonce} —— 防重放随机串（UUID 形式）。</li>
 * </ul>
 * </p>
 *
 * <p>紧凑构造器强制所有字符串字段非空白、{@code expiresAt} 非 {@code null}，以保证
 * 通过 {@link McpRequestStateCodec} 解码后的状态不可能"半残"。</p>
 *
 * @param payload              不透明负载
 * @param principalFingerprint 主体指纹
 * @param method               目标方法名
 * @param expiresAt            过期时间点
 * @param nonce                防重放随机串
 *
 * @author richie696
 * @since 2026-08-11
 */
public record McpRequestState(
        String payload,
        String principalFingerprint,
        String method,
        Instant expiresAt,
        String nonce) {
    /**
     * 紧凑构造器：所有字符串字段非空白、{@code expiresAt} 非 {@code null}。
     *
     * @throws IllegalArgumentException 当任一字段为空白或 {@code expiresAt} 为 {@code null} 时
     */
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
