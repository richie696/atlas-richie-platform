package cn.richie696.component.mcp.protocol.model;

import cn.richie696.component.mcp.protocol.McpProtocolEra;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 两个协议时代汇合后的内部请求。
 *
 * <p>为什么需要"归一化"层：2025-11-25 的 {@code initialize} 把"协议版本/对端信息/能力"
 * 放在请求顶层；2026-07-28 把它们迁到 {@code params._meta} 之下，业务侧若直接对接
 * 两种形态需要写两套解析。本 record 由 {@link cn.richie696.component.mcp.protocol.dialect.McpProtocolDialect}
 * 产出，提供：
 * <ul>
 *   <li>统一的 {@code protocolVersion / era / peer / capabilities / metadata} 字段；</li>
 *   <li>剥离版本相关元数据后的 {@code arguments}，业务只需关心真正的业务参数；</li>
 *   <li>{@link #notification()} 便捷判定（{@code id == null}）。</li>
 * </ul>
 * </p>
 *
 * @param id              JSON-RPC 请求 id；通知型请求为 {@code null}
 * @param method          调用的方法名
 * @param arguments       剥离元数据后的业务参数
 * @param protocolVersion 协议版本（已规整为本组件的常量）
 * @param era             所属协议时代
 * @param peer            对端实现身份（可能为 {@code null}，取决于时代与方法）
 * @param capabilities    对端能力声明
 * @param metadata        透传的元数据 Map（{@code _meta} 内容）
 *
 * @author richie696
 * @since 2026-08-11
 */
public record McpNormalizedRequest(
        Object id,
        String method,
        Map<String, Object> arguments,
        String protocolVersion,
        McpProtocolEra era,
        McpImplementationInfo peer,
        Map<String, Object> capabilities,
        Map<String, Object> metadata) {

    /**
     * 紧凑构造器：必填字段校验、可变 Map 拷贝为不可变。
     */
    public McpNormalizedRequest {
        method = Objects.requireNonNull(method, "method");
        arguments = arguments == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
        protocolVersion = Objects.requireNonNull(protocolVersion, "protocolVersion");
        era = Objects.requireNonNull(era, "era");
        capabilities = capabilities == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(capabilities));
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    /**
     * 判断该请求是否为 JSON-RPC 通知（无需返回响应）。
     *
     * @return {@code true} 表示通知型请求（{@code id == null}）
     */
    public boolean notification() {
        return id == null;
    }
}
