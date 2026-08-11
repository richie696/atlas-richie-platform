package cn.richie696.component.mcp.server.resource;

import cn.richie696.component.mcp.api.model.McpResourceTemplateDescriptor;
import cn.richie696.component.mcp.api.server.McpResourceHandler;

import java.util.Objects;

/**
 * MCP Resource 模板注册项：把 URI 模板与读取处理器绑定，用于支持参数化 URI 匹配。
 *
 * <p>与 {@link McpResourceRegistration} 的区别在于：模板描述符的 URI 字段是
 * RFC 6570 风格的占位符模板（如 {@code file:///{tenant}/docs/{id}}），
 * 在 {@link McpResourceRegistry#resolve} 时被转换为正则并匹配具体 URI，
 * 命中后动态生成一个 URI 已替换为实际值的资源描述符交给 handler 处理。</p>
 *
 * @param descriptor 资源模板描述符（含 URI 模板、名称、占位符定义等）
 * @param handler    处理匹配请求的处理器
 *
 * @author richie696
 * @since 2026-08-11
 */
public record McpResourceTemplateRegistration(
        McpResourceTemplateDescriptor descriptor,
        McpResourceHandler handler) {
    public McpResourceTemplateRegistration {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        handler = Objects.requireNonNull(handler, "handler");
    }
}
