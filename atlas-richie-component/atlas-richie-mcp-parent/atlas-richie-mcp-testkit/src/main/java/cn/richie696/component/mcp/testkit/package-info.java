/**
 * 包作用说明：本包为 MCP 组件的下游消费者（业务方单测、集成测试、二方库）提供"开箱即用"的协议/工具测试夹具，
 * 避免重复造测试数据。
 *
 * <p>本包核心类与职责：</p>
 * <ul>
 *   <li>{@link cn.richie696.component.mcp.testkit.McpProtocolFixtures}：构造符合 MCP 协议版本的 JSON-RPC 请求样例（含 2026-07-28 现代版与 2025-11-25 legacy 握手）。</li>
 *   <li>{@link cn.richie696.component.mcp.testkit.McpToolFixtures}：构造服务器端 {@code McpToolRegistration} / {@code McpCallContext} 测试样例，并提供统一的 JSON Schema 断言辅助方法。</li>
 * </ul>
 *
 * <p>把这些类放在同一个包的原因：它们都扮演"消费方测试基础设施"角色，依赖 API、protocol、server-core、schema 多个下游包，
 * 但自身不依赖 Spring / Spring Boot，确保业务侧无框架依赖即可复用。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
package cn.richie696.component.mcp.testkit;
