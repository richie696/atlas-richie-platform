/**
 * 现代 MCP Streamable HTTP 传输内核：将框架无关的端点、客户端、可观测结构等组件聚合在同一包，
 * 形成一个可被任意 Web 容器（Servlet / Reactive / 自研）接入的传输适配层。
 *
 * <p>本包内的类按职责划分为以下几组：
 * <ul>
 *   <li><b>框架中立契约</b>：{@link McpHttpRequest}、{@link McpHttpResponse}、{@link McpValidatedHttpRequest}
 *       —— 任何 Web 适配器只需把请求头/体映射为这些类型即可复用本模块的协议处理能力。</li>
 *   <li><b>端到端处理</b>：{@link McpServerHttpEndpoint} 是服务端核心；
 *       {@link McpHttpToolClient} 是轻量级客户端，用于调用远程 MCP 服务。</li>
 *   <li><b>协议校验</b>：{@link McpStreamableHttpRequestValidator} 负责 2026-07-28 协议版的
 *       Content-Type / Accept / Origin / 镜像 Header 等入站校验。</li>
 *   <li><b>运行时支撑</b>：{@link McpCancellationRegistry}（取消令牌注册表）、
 *       {@link McpSubscriptionManager}（订阅与通知分发）、{@link McpMrtrCoordinator}（多轮交互编排器）。</li>
 *   <li><b>策略与数据载体</b>：{@link McpOriginPolicy}（浏览器 CORS 策略）、
 *       {@link McpRemoteTool}（远端工具元数据）、{@link McpSubscriptionSpec}（订阅请求规格）。</li>
 *   <li><b>异常体系</b>：{@link McpHttpTransportException}（服务端映射到 HTTP 状态码）与
 *       {@link McpHttpClientException}（客户端统一异常，屏蔽底层 SDK 细节）。</li>
 *   <li><b>常量</b>：{@link McpHttpHeaders} 集中管理 Streamable HTTP 标准头，避免散落字符串字面量。</li>
 * </ul>
 *
 * <p>之所以把这些类放在同一个包：它们共同实现"Web 容器无关的 MCP 传输"这一抽象边界——这是 MCP 协议
 * 真正可移植到 Servlet / Spring MVC / WebFlux / 自研网关的唯一稳定接口。包内不允许出现任何
 * {@code jakarta.servlet}、{@code spring-web} 等 Web 框架依赖，确保传输内核可在不同运行时之间整体复用。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
package cn.richie696.component.mcp.transport.http;
