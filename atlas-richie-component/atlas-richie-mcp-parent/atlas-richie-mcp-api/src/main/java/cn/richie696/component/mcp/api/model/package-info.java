/**
 * MCP 协议无关的数据模型包：定义 Server/Client 之间交换的业务级数据载体。
 *
 * <p>本包内的所有类型都是不可变 {@code record}，与具体 MCP 协议版本解耦，承载 Tools/Resources/Prompts
 * 的描述符、Tool 调用结果、资源内容、Prompt 内容、补全结果等业务概念。这样设计的原因：</p>
 * <ul>
 *   <li>业务代码只面向稳定 record 编程，避免被协议层 JSON 字段变更"穿透"。</li>
 *   <li>协议适配层（HTTP/SSE/Stdio/...)负责 record 与协议 JSON 之间的互转。</li>
 *   <li>record 紧凑构造器统一做防御性拷贝与不可变包装，避免外部突变污染内部状态。</li>
 * </ul>
 *
 * <p>核心类型：</p>
 * <ul>
 *   <li>{@link cn.richie696.component.mcp.api.model.McpToolDescriptor}：Tool 的稳定描述（名称/输入输出
 *       Schema/注解）。</li>
 *   <li>{@link cn.richie696.component.mcp.api.model.McpToolResponse}：Tool 调用结果（含
 *       {@code complete} 与 {@code input_required} 两种 resultType）。</li>
 *   <li>{@link cn.richie696.component.mcp.api.model.McpResourceDescriptor}、{@link
 *       cn.richie696.component.mcp.api.model.McpResourceTemplateDescriptor}、{@link
 *       cn.richie696.component.mcp.api.model.McpResourceContent}：资源描述符、模板描述符与内容载体。</li>
 *   <li>{@link cn.richie696.component.mcp.api.model.McpPromptDescriptor}、{@link
 *       cn.richie696.component.mcp.api.model.McpPromptContent}：Prompt 描述符与多轮消息内容。</li>
 *   <li>{@link cn.richie696.component.mcp.api.model.McpCompletionResult}：参数自动补全结果（含分页信息）。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-08-11
 */
package cn.richie696.component.mcp.api.model;
