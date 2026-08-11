package cn.richie696.component.mcp.api;

import cn.richie696.component.mcp.api.model.McpCompletionResult;
import cn.richie696.component.mcp.api.model.McpPromptContent;
import cn.richie696.component.mcp.api.model.McpPromptDescriptor;
import cn.richie696.component.mcp.api.model.McpResourceContent;
import cn.richie696.component.mcp.api.model.McpResourceDescriptor;
import cn.richie696.component.mcp.api.model.McpResourceTemplateDescriptor;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.api.model.McpToolResponse;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * MCP operations for Discovery-driven clients.
 *
 * <p>Unlike {@link McpOperations}, each operation receives its endpoint and
 * request headers. This makes server selection and user/service credentials a
 * request concern while keeping protocol negotiation and transport in the
 * platform component.</p>
 *
 * <p>该接口存在的意义：在"先发现再调用"的多 MCP Server 场景下（典型如 Agent 网关从中心化
 * Registry 拉取可调用 Server 列表，再按用户身份选择目标），端点与凭据属于"每次请求"的属性，
 * 不能与 Spring 上下文生命周期绑定。{@code McpDynamicOperations} 通过把这两者下沉到
 * 参数级别，让上层 Agent 框架以最小代价复用同一份协议适配实现。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public interface McpDynamicOperations {
    /**
     * 列出目标 MCP Server 暴露的全部 Tool 描述。
     *
     * @param request 目标端点与凭据
     * @return Tool 描述列表的异步结果
     */
    CompletionStage<List<McpToolDescriptor>> listTools(McpClientRequest request);

    /**
     * 调用目标 MCP Server 上的指定 Tool。
     *
     * @param request 目标端点与凭据
     * @param toolName 业务 Tool 名称（与 {@link McpToolDescriptor#name()} 对齐）
     * @param arguments Tool 参数，键为参数名
     * @return Tool 调用结果（可能为 {@code input_required} 状态）的异步结果
     */
    CompletionStage<McpToolResponse> callTool(
            McpClientRequest request,
            String toolName,
            Map<String, Object> arguments);

    /**
     * 列出目标 MCP Server 暴露的 Resource 描述。
     *
     * @param request 目标端点与凭据
     * @return Resource 描述列表的异步结果
     */
    CompletionStage<List<McpResourceDescriptor>> listResources(McpClientRequest request);

    /**
     * 列出目标 MCP Server 的 Resource 模板描述（参数化 URI）。
     *
     * <p>默认实现直接返回 {@link UnsupportedOperationException}，因为并非所有 MCP Server
     * 都支持模板资源；不支持的协议版本应保持此默认行为而非静默成功。</p>
     *
     * @param request 目标端点与凭据
     * @return Resource 模板列表的异步结果
     */
    default CompletionStage<List<McpResourceTemplateDescriptor>> listResourceTemplates(
            McpClientRequest request) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Resource templates are not supported"));
    }

    /**
     * 对 Tool/Prompt 参数发起自动补全请求。
     *
     * <p>默认实现返回 {@link UnsupportedOperationException}；仅支持补全协议的 MCP Server
     * 需覆盖此方法。</p>
     *
     * @param request 目标端点与凭据
     * @param reference 引用信息（被补全参数所属的 Tool 或 Prompt）
     * @param argumentName 被补全的参数名
     * @param value 当前已输入的字符串
     * @param contextArguments 与该参数同级的其他参数（用于上下文敏感补全）
     * @return 补全候选值列表的异步结果
     */
    default CompletionStage<McpCompletionResult> complete(
            McpClientRequest request,
            Map<String, Object> reference,
            String argumentName,
            String value,
            Map<String, String> contextArguments) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Completions are not supported"));
    }

    /**
     * 读取目标 MCP Server 上的指定 Resource。
     *
     * @param request 目标端点与凭据
     * @param uri 资源 URI（与 {@link McpResourceDescriptor#uri()} 对齐）
     * @return Resource 内容的异步结果
     */
    CompletionStage<McpResourceContent> readResource(McpClientRequest request, String uri);

    /**
     * 列出目标 MCP Server 暴露的 Prompt 描述。
     *
     * @param request 目标端点与凭据
     * @return Prompt 描述列表的异步结果
     */
    CompletionStage<List<McpPromptDescriptor>> listPrompts(McpClientRequest request);

    /**
     * 获取目标 MCP Server 上指定 Prompt 的渲染结果。
     *
     * @param request 目标端点与凭据
     * @param name Prompt 名称
     * @param arguments 模板参数
     * @return 多轮消息内容的异步结果
     */
    CompletionStage<McpPromptContent> getPrompt(
            McpClientRequest request,
            String name,
            Map<String, Object> arguments);
}
