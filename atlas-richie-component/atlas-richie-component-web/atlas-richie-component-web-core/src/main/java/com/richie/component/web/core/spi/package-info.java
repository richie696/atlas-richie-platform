/**
 * SPI 层（容器无关）：纯 Java。
 * <p>
 * 本包是 atlas-richie-component-web 的 SPI 内核，依据 {@code README.md} §3.1 三层模型：
 *
 * <pre>
 * ┌─────────────────────────────────────────────────────────┐
 * │  适配层：InterceptingFilter (jakarta.servlet.Filter)     │   web-tomcat / web-jetty
 * │  ├─ 装配请求上下文（WebRequestContext 适配）              │
 * │  └─ 调度 interceptor chain                                │
 * ├─────────────────────────────────────────────────────────┤
 * │  SPI 层（本包）：纯 Java                                  │
 * │  WebRequestContext / WebInterceptor / Chain / Decision     │
 * └─────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>enforcer 规则</h2>
 * <ul>
 *   <li>本包下任何类<strong>不得</strong> import {@code jakarta.servlet.*} / {@code org.apache.catalina.*} /
 *       {@code org.eclipse.jetty.*} / {@code org.springframework.web.servlet.*} 等容器/框架专属 API。</li>
 *   <li>具体实现（读取 HTTP body / 写响应体）由 web-tomcat 与 web-jetty 的适配层完成。</li>
 *   <li>本包仅承载接口与事件载荷，便于 mock 与测试。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-07
 */
package com.richie.component.web.core.spi;