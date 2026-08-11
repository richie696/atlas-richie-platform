package cn.richie696.component.mcp.transport.http;

import cn.richie696.component.mcp.protocol.model.McpJsonRpcRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 与 Servlet/Reactive HTTP 框架无关的 MCP 请求载体。
 *
 * <p>本类的存在是为了让 {@link McpServerHttpEndpoint} 内部逻辑可以做到：
 * 测试用例可以直接用 {@code new McpHttpRequest(...)} 构造请求而无须启动 MockMvc/WebTestClient；
 * Spring MVC、Spring WebFlux、Helidon、Vert.x 等任意适配器只需把各自框架的入站对象映射为本 record，
 * 就能复用协议处理路径。它是 MCP 传输层"框架中立契约"的一部分。</p>
 *
 * <p>唯一构造时会执行防御性拷贝：所有 headers 值深拷贝为只读视图，以防止调用方后续对集合的修改
 * 影响本对象的不可变状态，特别在 Web 容器复用请求对象（HTTP/2 Stream、Reactor `Flux.fromIterable`）
 * 时是常见 bug 来源。</p>
 *
 * @param httpMethod  HTTP 方法，当前协议仅支持 {@code POST}
 * @param headers     多值 HTTP 头（外部 Map 在构造时深拷贝为不可变视图）
 * @param message     已解析出的 JSON-RPC 请求对象，body 为空时允许为 null
 * @author richie696
 * @since 2026-08-11
 */
public record McpHttpRequest(
        String httpMethod,
        Map<String, List<String>> headers,
        McpJsonRpcRequest message) {
    public McpHttpRequest {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        if (headers != null) {
            headers.forEach((name, values) -> copy.put(
                    name,
                    values == null
                            ? List.of()
                            : Collections.unmodifiableList(new ArrayList<>(values))));
        }
        headers = Collections.unmodifiableMap(copy);
    }
}
