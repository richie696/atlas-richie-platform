/**
 * MCP 协议方言（dialect）包：实现"单协议版本 ↔ 归一化模型"之间的防腐层（anti-corruption layer）。
 *
 * <p>设计意图：MCP 在 2025-11-25（会话/initialize 协议）与 2026-07-28（无状态协议）
 * 之间存在结构性差异——参数命名、握手方式、版本透传位置、能力声明字段都不一致。
 * 通过 {@link cn.richie696.component.mcp.protocol.dialect.McpProtocolDialect} 接口
 * 把这些差异封装起来，业务侧只需面对 {@code McpNormalizedRequest} /
 * {@code McpNormalizedResult}；新增协议版本只需新增一个 {@code McpProtocolDialect}
 * 实现并注册到 {@link cn.richie696.component.mcp.protocol.dialect.McpDialectRegistry}。
 *
 * 核心类职责：
 * <ul>
 *   <li>{@link cn.richie696.component.mcp.protocol.dialect.McpProtocolDialect}：
 *       方言接口，定义版本号、时代、请求归一化、结果归一化与编码五个契约。</li>
 *   <li>{@link cn.richie696.component.mcp.protocol.dialect.Mcp20260728Dialect}：
 *       2026-07-28 无状态协议时代适配器，强制 {@code _meta.protocolVersion} 与头部一致。</li>
 *   <li>{@link cn.richie696.component.mcp.protocol.dialect.Mcp20251125Dialect}：
 *       2025-11-25 会话/initialize 协议时代适配器，{@code initialize} 请求携带能力与对端信息。</li>
 *   <li>{@link cn.richie696.component.mcp.protocol.dialect.McpDialectRegistry}：
 *       以协议版本为键选择 Dialect 的注册表；构造期检测重复版本号。</li>
 *   <li>{@link cn.richie696.component.mcp.protocol.dialect.DialectSupport}：
 *       包内私有工具类，集中处理"对象/字符串/实现信息"三种入参的解析与错误归一化。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-08-11
 */
package cn.richie696.component.mcp.protocol.dialect;
