package cn.richie696.component.mcp.protocol.dialect;

import cn.richie696.component.mcp.protocol.McpProtocolEra;
import cn.richie696.component.mcp.protocol.model.McpJsonRpcRequest;
import cn.richie696.component.mcp.protocol.model.McpNormalizedRequest;
import cn.richie696.component.mcp.protocol.model.McpNormalizedResult;

import java.util.Map;

/**
 * 单个 MCP 协议版本与归一化模型之间的防腐层。
 *
 * <p>接口契约包含五个方法，分别承担：
 * <ul>
 *   <li>{@link #version()} —— 标识本方言对应的协议版本号；</li>
 *   <li>{@link #era()} —— 标识本方言所属的协议时代；</li>
 *   <li>{@link #normalizeRequest(McpJsonRpcRequest, String)} —— 把线格式请求转为
 *       {@link McpNormalizedRequest}；</li>
 *   <li>{@link #normalizeResult(Map)} —— 把线格式响应负载转为 {@link McpNormalizedResult}；</li>
 *   <li>{@link #encodeResult(McpNormalizedResult)} —— 把内部结果反向编码为线格式响应。</li>
 * </ul>
 * </p>
 *
 * <p>实现要点：每个方法都应是"纯函数"语义——给定相同入参应返回相同结果，便于测试与回放。
 * 不应在此层做 I/O 或持有可变状态。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public interface McpProtocolDialect {
    /**
     * 返回本方言对应的协议版本号。
     *
     * @return 协议版本字符串（如 {@code "2026-07-28"}）
     */
    String version();

    /**
     * 返回本方言所属的协议时代。
     *
     * @return 协议时代枚举
     */
    McpProtocolEra era();

    /**
     * 把 JSON-RPC 线格式请求归一化为内部模型。
     *
     * @param request                  线格式请求
     * @param transportProtocolVersion 来自传输层的协议版本（可为 {@code null}），用于跨字段一致性校验
     * @return 归一化后的内部请求
     */
    McpNormalizedRequest normalizeRequest(McpJsonRpcRequest request, String transportProtocolVersion);

    /**
     * 把线格式响应负载归一化为内部结果。
     *
     * @param result 线格式响应 Map
     * @return 归一化结果
     */
    McpNormalizedResult normalizeResult(Map<String, Object> result);

    /**
     * 把内部结果编码为线格式响应。
     *
     * @param result 内部结果
     * @return 线格式响应 Map
     */
    Map<String, Object> encodeResult(McpNormalizedResult result);
}
