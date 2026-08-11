package cn.richie696.component.mcp.transport.http;

/**
 * 集中维护 Streamable HTTP 与 镜像 Header 协议使用的所有标准 HTTP 头名称。
 *
 * <p>现代 MCP 协议除了 JSON-RPC 在请求/响应体内描述方法之外，还在 HTTP 头中冗余/镜像若干字段以满足
 * 网关、审计、负载均衡等中间件的观察需求（这些中间件通常只解析 HTTP 头而不解析 body）。本类
 * 作为这些头名常量的单一权威，避免散落在代码各处的字符串字面量导致的拼写错误与升级阻力。</p>
 *
 * <p>主要使用场景：
 * <ul>
 *   <li>{@link #PROTOCOL_VERSION} —— 协议版本必须在 HTTP 头与 body 内同时携带，且必须一致，
 *       见 {@link McpStreamableHttpRequestValidator}。</li>
 *   <li>{@link #METHOD} / {@link #NAME} / {@link #PARAMETER_PREFIX} —— 镜像 JSON-RPC method / params.name /
 *       params.arguments 中的可表达为 ASCII 的字段，方便外部记录与路由。</li>
 *   <li>{@link #ACCEPT} / {@link #CONTENT_TYPE} / {@link #ORIGIN} —— 标准 HTTP 协商与 CORS 头。</li>
 * </ul></p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpHttpHeaders {
    public static final String ACCEPT = "Accept";
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String ORIGIN = "Origin";
    public static final String PROTOCOL_VERSION = "MCP-Protocol-Version";
    public static final String METHOD = "Mcp-Method";
    public static final String NAME = "Mcp-Name";
    public static final String PARAMETER_PREFIX = "Mcp-Param-";

    private McpHttpHeaders() {
    }
}
