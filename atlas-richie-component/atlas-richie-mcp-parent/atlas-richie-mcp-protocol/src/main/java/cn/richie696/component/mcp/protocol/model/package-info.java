/**
 * MCP 协议数据模型包：定义线协议（wire）与归一化（normalized）两套不可变记录类型。
 *
 * <p>为什么把这两种模型放在同一包：
 * <ul>
 *   <li>JSON-RPC 2.0 的 {@code Request / Response / Error} 是与具体协议版本无关的
 *       最小线格式，由传输层直接产出，模型字段保持 1:1 对应 JSON，便于在编解码阶段做
 *       完整校验而不损失信息。</li>
 *   <li>{@code Normalized} 系列是方言层（{@code dialect}）的产物：把不同协议时代
 *       的"启动握手 / 元数据透传 / 能力声明"统一为同一形态，业务侧只需面向
 *       {@link cn.richie696.component.mcp.protocol.model.McpNormalizedRequest} 编程。</li>
 *   <li>{@link cn.richie696.component.mcp.protocol.model.McpImplementationInfo}
 *       表达"对端实现身份"（name/version/title/description/websiteUrl/icons），
 *       是两个时代都要上报的最小自描述载体，单独建模可避免重复拷贝。</li>
 * </ul>
 * </p>
 *
 * <p>核心类职责：
 * <ul>
 *   <li>{@link cn.richie696.component.mcp.protocol.model.McpJsonRpcRequest}：
 *       JSON 解码后的最小线格式；传输模块负责将 JSON 映射到该类型。</li>
 *   <li>{@link cn.richie696.component.mcp.protocol.model.McpJsonRpcResponse}：
 *       JSON-RPC 2.0 响应线格式；构造期强制 {@code result} 与 {@code error} 二选一。</li>
 *   <li>{@link cn.richie696.component.mcp.protocol.model.McpJsonRpcError}：
 *       JSON-RPC 2.0 错误对象（code/message/data）。</li>
 *   <li>{@link cn.richie696.component.mcp.protocol.model.McpNormalizedRequest}：
 *       两个协议时代汇合后的内部请求。</li>
 *   <li>{@link cn.richie696.component.mcp.protocol.model.McpNormalizedResult}：
 *       归一化结果显式携带完成状态（COMPLETE / INPUT_REQUIRED），兼容旧版本缺省语义。</li>
 *   <li>{@link cn.richie696.component.mcp.protocol.model.McpImplementationInfo}：
 *       对端实现身份描述，提供线格式与领域对象互转。</li>
 * </ul>
 * </p>
 *
 * @author richie696
 * @since 2026-08-11
 */
package cn.richie696.component.mcp.protocol.model;
