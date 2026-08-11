package cn.richie696.component.mcp.protocol.compatibility;

import cn.richie696.component.mcp.protocol.McpProtocolVersions;

import java.time.Instant;
import java.util.Objects;

/**
 * 单个远端 MCP server 已选定的协议版本（含过期时间）。
 *
 * <p>为什么需要 record + 紧凑构造器强校验：本类会被写入
 * {@link McpProtocolEraCache}，在缓存期内的任意位置被读取。任何构造期漏掉的非法值
 * （如空白版本、不在白名单的版本、{@code null} 过期时间）都可能在后续探测循环中
 * 引发难以排查的问题——所以全部前置到构造器校验。</p>
 *
 * @param version   已选定的协议版本
 * @param expiresAt 过期时间点（{@link Instant}）
 *
 * @author richie696
 * @since 2026-08-11
 */
public record McpNegotiatedProtocol(String version, Instant expiresAt) {
    /**
     * 紧凑构造器：强制 {@code version} 非空且在本组件白名单内、{@code expiresAt} 非 {@code null}。
     *
     * @throws IllegalArgumentException 当版本空白或不在 {@link McpProtocolVersions#SUPPORTED} 中时
     */
    public McpNegotiatedProtocol {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        if (!McpProtocolVersions.SUPPORTED.contains(version)) {
            throw new IllegalArgumentException("Unsupported MCP protocol version: " + version);
        }
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    /**
     * 判断当前时间是否已超过过期时间。
     *
     * @param now 当前时间
     * @return {@code true} 表示已过期（即 {@code now >= expiresAt}）
     */
    public boolean expired(Instant now) {
        return !expiresAt.isAfter(Objects.requireNonNull(now, "now"));
    }
}
