package cn.richie696.component.mcp.api;

import cn.richie696.component.mcp.api.model.McpPromptContent;
import cn.richie696.component.mcp.api.model.McpPromptDescriptor;
import cn.richie696.component.mcp.api.model.McpResourceContent;
import cn.richie696.component.mcp.api.model.McpResourceDescriptor;
import cn.richie696.component.mcp.api.model.McpResourceTemplateDescriptor;
import cn.richie696.component.mcp.api.model.McpCompletionResult;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.api.model.McpToolResponse;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * 业务侧唯一需要依赖的 MCP Client 操作面。
 *
 * <p>该接口是预配置型 MCP Client 的统一门面：通过 {@code serverId} 索引一组在
 * {@code application.yml} 中预声明的 MCP Server，并对外暴露标准 MCP 操作。
 * 业务侧只需要注入该接口即可发起 Tool 调用、读取 Resource、获取 Prompt，无需关心
 * 端点、凭据、协议版本等细节。</p>
 *
 * <p>适用与不适用：</p>
 * <ul>
 *   <li>适用：Server 集合在启动时已确定、变更低频的场景，例如企业内部工具集成；</li>
 *   <li>不适用：运行时动态发现 Server 的场景，应改用 {@link McpDynamicOperations}。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-08-11
 */
public interface McpOperations {
    /**
     * 列出指定 Server 暴露的全部 Tool 描述。
     *
     * @param serverId 配置文件中的 Server 标识
     * @return Tool 描述列表的异步结果
     */
    CompletionStage<List<McpToolDescriptor>> listTools(String serverId);

    /**
     * 调用指定 Server 上的 Tool。
     *
     * @param serverId Server 标识
     * @param toolName Tool 业务名
     * @param arguments Tool 参数
     * @return Tool 调用结果的异步结果
     */
    CompletionStage<McpToolResponse> callTool(String serverId, String toolName, Map<String, Object> arguments);

    /**
     * 列出指定 Server 暴露的 Resource。
     *
     * @param serverId Server 标识
     * @return Resource 描述列表的异步结果
     */
    CompletionStage<List<McpResourceDescriptor>> listResources(String serverId);

    /**
     * 列出指定 Server 的 Resource 模板。
     *
     * <p>默认实现返回 {@link UnsupportedOperationException}，与 {@link McpDynamicOperations}
     * 保持一致的语义：不支持模板资源的实现不需要覆盖此方法。</p>
     *
     * @param serverId Server 标识
     * @return Resource 模板列表的异步结果
     */
    default CompletionStage<List<McpResourceTemplateDescriptor>> listResourceTemplates(String serverId) {
        return java.util.concurrent.CompletableFuture.failedFuture(
                new UnsupportedOperationException("Resource templates are not supported"));
    }

    /**
     * 对指定 Server 的 Tool/Prompt 参数发起补全请求。
     *
     * <p>默认实现返回 {@link UnsupportedOperationException}。</p>
     *
     * @param serverId Server 标识
     * @param reference 引用信息（被补全参数所属的 Tool 或 Prompt）
     * @param argumentName 被补全的参数名
     * @param value 当前已输入字符串
     * @param contextArguments 同级其他参数
     * @return 补全候选值列表的异步结果
     */
    default CompletionStage<McpCompletionResult> complete(
            String serverId,
            Map<String, Object> reference,
            String argumentName,
            String value,
            Map<String, String> contextArguments) {
        return java.util.concurrent.CompletableFuture.failedFuture(
                new UnsupportedOperationException("Completions are not supported"));
    }

    /**
     * 读取指定 Server 上的 Resource。
     *
     * @param serverId Server 标识
     * @param uri 资源 URI
     * @return Resource 内容的异步结果
     */
    CompletionStage<McpResourceContent> readResource(String serverId, String uri);

    /**
     * 列出指定 Server 暴露的 Prompt。
     *
     * @param serverId Server 标识
     * @return Prompt 描述列表的异步结果
     */
    CompletionStage<List<McpPromptDescriptor>> listPrompts(String serverId);

    /**
     * 获取指定 Server 上的 Prompt 渲染结果。
     *
     * @param serverId Server 标识
     * @param name Prompt 名称
     * @param arguments 模板参数
     * @return 多轮消息内容的异步结果
     */
    CompletionStage<McpPromptContent> getPrompt(String serverId, String name, Map<String, Object> arguments);
}
