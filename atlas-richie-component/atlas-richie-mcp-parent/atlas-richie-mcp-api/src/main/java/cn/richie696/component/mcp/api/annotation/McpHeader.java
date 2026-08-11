package cn.richie696.component.mcp.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that an argument may be mirrored into an MCP HTTP parameter header.
 *
 * <p>当 {@code @McpTool} 方法需要从 MCP parameter header 中取值时，可在对应参数上标注
 * {@code @McpHeader("X-Tenant-Id")}；框架会在调用时把同名 header 值注入到该参数，
 * 业务方法即可像读取普通参数一样读取它。这样设计的目的是让"基于 header 透传的鉴权/租户
 * 维度"也能参与 Tool 的输入校验与审计，而不是仅靠拦截器在 {@link cn.richie696.component.mcp.api.McpCallContext}
 * 之外的隐藏通道传递。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpHeader {
    /**
     * 要读取的 MCP parameter header 名。
     *
     * @return header 名（如 {@code X-Tenant-Id}）
     */
    String value();
}
