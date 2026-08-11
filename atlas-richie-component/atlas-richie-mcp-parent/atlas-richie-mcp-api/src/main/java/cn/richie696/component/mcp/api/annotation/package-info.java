/**
 * MCP 业务元数据注解：把 Java/Kotlin 方法声明为 MCP 协议可见的 Tool 或 Tool 参数。
 *
 * <p>本包内的注解是业务方法暴露成 MCP Tool 的唯一入口；运行时由
 * {@code atlas-richie-mcp-server} 模块扫描这些注解，并组装为 {@code McpToolDefinition}。</p>
 *
 * <p>核心注解：</p>
 * <ul>
 *   <li>{@link cn.richie696.component.mcp.api.annotation.McpTool}：标注在方法上，声明该方法为一个 MCP Tool，
 *       含幂等性、只读、破坏性、开放世界、所需 Scope 等业务属性。</li>
 *   <li>{@link cn.richie696.component.mcp.api.annotation.McpArgument}：标注在 Tool 方法参数上，声明参数名、
 *       描述、必填、默认值、JSON Schema 约束（format/minimum/maximum/enum 等）以及是否敏感字段。</li>
 *   <li>{@link cn.richie696.component.mcp.api.annotation.McpHeader}：标注在 Tool 方法参数上，声明该参数可
 *       被镜像到 MCP HTTP 协议的 parameter header（用于鉴权透传等场景）。</li>
 * </ul>
 *
 * <p>将这些注解聚合在 {@code annotation} 子包的目的是：与 {@code model}/{@code server} 包的运行期
 * 数据结构解耦，编译期仅依赖 JDK 自带的 {@code java.lang.annotation}，不会引入任何业务运行期依赖。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
package cn.richie696.component.mcp.api.annotation;
