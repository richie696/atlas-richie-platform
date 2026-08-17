/**
 * 包作用说明：本包是 atlas-richie-mcp-parent 中 MCP 客户端侧的 Spring Boot Starter，
 * 通过自动装配把 HTTP 客户端、协议协商、OAuth、结果缓存、能力门面等装配为 Spring 容器内的 Bean，
 * 业务应用只需引入该 Starter 即可开箱使用。
 *
 * <p>本包核心类与职责：
 * <ul>
 *   <li>{@link cn.richie696.component.mcp.client.spring.boot.McpClientAutoConfiguration}：Spring Boot 自动配置入口，按条件注册 HTTP 客户端、协议 Era 缓存、结果缓存、OAuth 客户端与 Operations 门面。</li>
 *   <li>{@link cn.richie696.component.mcp.client.spring.boot.McpClientProperties}：{@code platform.component.mcp.client.*} 配置树，含连接/请求超时、协议版本、缓存策略、多服务端定义、OAuth 子配置。</li>
 *   <li>{@link cn.richie696.component.mcp.client.spring.boot.McpHttpOperations}：Operations 门面的 HTTP 实现，统一处理协议协商、结果缓存、OAuth 注入与多 serverId 路由。</li>
 *   <li>{@link cn.richie696.component.mcp.client.spring.boot.McpClientResultCache}：进程内 TTL 结果缓存（list/discovery），支持按 serverId 前缀失效。</li>
 *   <li>{@link cn.richie696.component.mcp.client.spring.boot.McpClientCacheControl}：暴露给上层使用方的缓存失效 SPI，配合 list_changed / resource_updated 通知主动清理。</li>
 * </ul>
 *
 * <p>把这些类放在同一个包的原因：它们在客户端 Starter 的同一装配边界内协同工作，
 * 既能被 {@code META-INF/spring/...AutoConfiguration.imports} 自动发现，又只依赖 {@code transport-http}、
 * {@code protocol}、{@code security-oauth} 等下游模块，避免反向依赖 server-core。
 *
 * @author richie696
 * @since 2026-08-11
 */
package cn.richie696.component.mcp.client.spring.boot;
