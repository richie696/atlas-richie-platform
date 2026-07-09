/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.web.core.config;

import com.richie.component.web.core.config.business.BusinessIntegrationProperties;
import com.richie.component.web.core.config.degrade.DegradeProperties;
import com.richie.component.web.core.config.hang.HangDetectionProperties;
import com.richie.component.web.core.config.login.LoginConfig;
import com.richie.component.web.core.config.mvc.CorsProperties;
import com.richie.component.web.core.config.protection.PlatformProtectionProperties;
import com.richie.component.web.core.config.ratelimit.CircuitBreakerProperties;
import com.richie.component.web.core.config.ratelimit.RateLimitProperties;
import com.richie.component.web.core.config.ratelimit.WebFilterProperties;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.Locale;

/**
 * 平台 Web 主配置（前缀：{@code platform.component.web}）—— 聚合根。
 * <p>
 * 本类按 GatewayConfig 模式作为<strong>纯聚合</strong>：仅保留 web 主控身份字段（{@link #defaultLocale}），
 * 其它 web 子域（Login / CORS / RateLimit / CircuitBreaker / Hang / Degrade / Protection / Business / Filter）
 * 通过 {@link NestedConfigurationProperty} 以 {@code new} 实例方式持有，绑定到各自子前缀。
 *
 * <h2>启用 Gateway 时的禁用约定（与 atlas-richie-gateway-service 的职责对照）</h2>
 * <p>当部署 <strong>atlas-richie-gateway-service</strong> 后，gateway 端已通过
 * {@code GatewayConfig} + 子 Config（{@code TokenFilter} / {@code CorsFilter} /
 * {@code TenantFilter} / {@code DuplicateSubmitConfig} / {@code SecurityFilter} /
 * {@code FallbackConfig} 等）接管部分 web 子域职责。下表列出每个子域的 gateway 关系与处置方式：
 *
 * <table border="1">
 *   <caption>Web 子域 vs Gateway 职责对照</caption>
 *   <tr>
 *     <th>子域字段</th><th>对应 Properties</th><th>Gateway 是否已接管</th><th>gateway 模式下处置</th>
 *   </tr>
 *   <tr>
 *     <td>{@link #login}</td>
 *     <td>{@link LoginConfig}</td>
 *     <td>✅ {@code TokenFilter} 已签发</td>
 *     <td><strong style="color:#c00">必须禁用</strong>（web 端 {@code IssueTokenAdvice} 旁路）</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #cors}</td>
 *     <td>{@link CorsProperties}</td>
 *     <td>✅ {@code CorsFilter} 已签头</td>
 *     <td><strong style="color:#c00">必须禁用</strong>（否则双 {@code Access-Control-Allow-*}）</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #business}.tenant</td>
 *     <td>{@code BusinessIntegrationProperties.Tenant}</td>
 *     <td>✅ {@code TenantFilter} 已解析</td>
 *     <td><strong>建议禁用</strong>（重复解析 + gateway 已规范化 {@code X-Tenant-Id}）</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #business}.idempotency</td>
 *     <td>{@code BusinessIntegrationProperties.Idempotency}</td>
 *     <td>✅ {@code DuplicateSubmitConfig} 已检查</td>
 *     <td><strong>建议禁用</strong>（双层检查易误判）</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #business}.api-version</td>
 *     <td>{@code BusinessIntegrationProperties.ApiVersion}</td>
 *     <td>❌ gateway 不解析</td>
 *     <td>✅ 可独立启用（controller 路由级版本协商）</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #rateLimit}</td>
 *     <td>{@link RateLimitProperties}</td>
 *     <td>❌ 永不承载反压（反压由 gateway {@code SecurityFilter} 或 <strong>Sentinel MVC 适配器</strong>承担，必须在 Servlet 容器线程池<strong>之前</strong>生效）</td>
 *     <td>✅ 业务接口级流量整形：按 {@link RateLimitProperties#getRoutes() path 路由}配置业务规则（VIP / 普通用户 / 敏感接口）</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #circuitBreaker}</td>
 *     <td>{@link CircuitBreakerProperties}</td>
 *     <td>❌ 永不承载反压（同上，反压在容器线程池<strong>之前</strong>处理）</td>
 *     <td>✅ 业务接口级熔断：按 {@link CircuitBreakerProperties#getRoutes() path 隔离}独立 CB 状态机，保护下游资源</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #filter}.keyHeader</td>
 *     <td>{@link WebFilterProperties}</td>
 *     <td>⚠ gateway 透传 header</td>
 *     <td><strong>保留但需对齐</strong>（keyHeader 必须与 gateway 透传的 client header 一致）</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #hang}</td>
 *     <td>{@link HangDetectionProperties}</td>
 *     <td>⚠ gateway 有 timeout（不同维度）</td>
 *     <td>✅ 可共存（gateway 看总耗时，web 看拦截器链内耗时）</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #degrade}</td>
 *     <td>{@link DegradeProperties}</td>
 *     <td>❌ 永不承载反压 / 上游兜底（反压在容器线程池<strong>之前</strong>处理，本子域仅做业务级差异化降级响应）</td>
 *     <td>✅ 业务接口级降级响应：按 {@link DegradeProperties#getRoutes() path 路由}返回业务特定 code/msg</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #protection}.request-size</td>
 *     <td>{@code PlatformProtectionProperties.RequestSize}</td>
 *     <td>✅ gateway body size 限制</td>
 *     <td><strong>建议禁用</strong>（网关层已限制）</td>
 *   </tr>
 * </table>
 *
 * <p>YAML 配置示例（gateway 部署后推荐配置）：
 * <pre>{@code
 * platform:
 *   component:
 *     web:
 *       # 必须禁用（gateway 已接管，否则双签发/双跨域头）
 *       login:
 *         enabled: false
 *       cors:
 *         enabled: false
 *       # 建议禁用（gateway 已有同类基础设施能力）
 *       business:
 *         tenant:
 *           require-on-missing: false                # 或整体 tenant 段留空
 *         idempotency:
 *           deny-status: 0                           # 0 = 关闭兜底
 *       protection:
 *         request-size:
 *           deny-code: ""                            # 或留空关闭
 *       # 保留但需对齐（gateway 透传的 header 名）
 *       filter:
 *         key-header: X-Client-Id                    # 必须与 gateway SecurityFilterConfig.clientHeader 一致
 *       # 可共存（gateway = 反压级 / web = 业务接口级，维度不同）
 *       rate-limit:                                  # ← web 端业务限流（按 path 路由）
 *         routes:
 *           "/api/v1/orders/**":
 *             permits-per-second: 5                   # 业务规则：限某业务接口 5 req/s
 *       circuit-breaker:                             # ← web 端业务熔断（按被保护资源）
 *         routes:
 *           "/api/v1/orders/**":
 *             failure-rate-threshold: 30
 *       degrade:                                     # ← web 端业务降级（按 path 返回特定 code/msg）
 *         routes:
 *           "/api/v1/orders/**":
 *             code: ORDER_DEGRADED                    # 业务降级响应码
 *       hang:                                        # gateway 看总耗时，web 看方法耗时 + 线程栈
 *         warn-ms: 30000
 *       business.api-version:                        # controller 路由级版本协商
 *         default-version: default
 *       protection:
 *         request-size: ...
 * }</pre>
 *
 * <h2>YAML 结构</h2>
 * <pre>{@code
 * platform:
 *   component:
 *     web:
 *       default-locale: zh-CN                       # ← WebProperties 直接字段
 *       login:                                      # ← LoginConfig 子域
 *         login-urls: [...]
 *         token-secret: ...
 *         token-expiration-date: ...
 *       cors:                                       # ← CorsProperties 子域
 *         enabled: true
 *         ...
 *       rate-limit:                                 # ← RateLimitProperties 子域
 *         permits-per-second: 50
 *         ...
 *       circuit-breaker:                            # ← CircuitBreakerProperties 子域
 *         failure-rate-threshold: 50
 *         ...
 *       filter:                                     # ← WebFilterProperties 子域
 *         key-header: X-Client-Id
 *       hang:                                       # ← HangDetectionProperties 子域
 *         warn-ms: 30000
 *         ...
 *       degrade:                                    # ← DegradeProperties 子域
 *         enabled: true
 *         ...
 *       protection:                                 # ← PlatformProtectionProperties 子域
 *         request-size: ...
 *       business:                                   # ← BusinessIntegrationProperties 子域
 *         tenant: ...
 *         idempotency: ...
 *         api-version: ...
 * }</pre>
 *
 * <h2>主配置 vs 子域</h2>
 * <ul>
 *   <li><strong>主配置字段</strong>：仅 {@link #defaultLocale}——属于 web 层主控 i18n 兜底。</li>
 *   <li><strong>子域配置</strong>：每个子域一个独立 {@code *Properties} 类，绑定各自子前缀；本类通过
 *       {@code @NestedConfigurationProperty + new} 持有引用，使业务方可以一次性 {@code @Autowired WebProperties}
 *       拿到全量配置，也支持按子域独立注入。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-07
 */
@Data
@ConfigurationProperties(prefix = "platform.component.web")
public class WebProperties {

    /**
     * 默认 Locale（IETF BCP 47 语言标签，如 zh-CN、en-US）。
     * <p>当 i18n 组件同时存在时，建议两边配置保持一致。默认值：{@code Locale.CHINA}。
     * <p><strong>主控字段</strong>——属于 web 身份级别，不下沉子域。
     */
    private Locale defaultLocale = Locale.CHINA;

    // ─────────── 子域配置（new 实例 + @NestedConfigurationProperty 绑定子前缀）───────────

    /**
     * 登录 / Token 签发子域。前缀：{@code platform.component.web.login}。
     * <p><strong>Gateway 模式下禁用</strong>：当部署 atlas-richie-gateway-service 时，token 签发由 gateway
     * 完成（web 端 {@code IssueTokenAdvice} 整个被旁路），本子域配置<strong>必须保持禁用 / 留空</strong>，
     * 否则双签发会导致 token 校验冲突。建议显式 {@code platform.component.web.login.enabled=false}。
     */
    @NestedConfigurationProperty
    private LoginConfig login = new LoginConfig();

    /**
     * CORS 跨域子域。前缀：{@code platform.component.web.cors}。
     * <p><strong>Gateway 模式下禁用</strong>：CORS 跨域头由 gateway 统一签发，web 端再写
     * {@code Access-Control-Allow-*} 会与 gateway 重复，导致浏览器拿到多个冲突的跨域头。
     * 部署 gateway 时建议显式 {@code platform.component.web.cors.enabled=false}。
     */
    @NestedConfigurationProperty
    private CorsProperties cors = new CorsProperties();

    /**
     * 业务接口级限流子域。前缀：{@code platform.component.web.rate-limit}。
     * <p><strong style="color:#c00">职责定位（绝对断言）</strong>：本子域<strong>仅承载业务接口级流量整形</strong>，
     * <strong>永远不承担反压保护职责</strong>。
     * <ul>
     *   <li><strong>反压保护</strong>（防止上游流量把 Servlet 容器线程池打爆）必须由<strong>容器线程池之前</strong>的层处理：
     *       <ul>
     *         <li>部署 gateway 时：atlas-richie-gateway-service 端 {@code SecurityFilter}（IP / UA 频控）</li>
     *         <li>不部署 gateway 时：<strong>Sentinel MVC 适配器</strong>（直接在 jetty / tomcat 容器线程池侧保护）</li>
     *       </ul>
     *   </li>
     *   <li><strong>为什么不在这里做反压？</strong>本组件的拦截器运行在 Servlet 容器线程池<strong>之后</strong>——
     *       大流量进入时容器已经先挂了，运行在后面的限流没机会救场。</li>
     *   <li><strong>本子域的正确用法</strong>：按 {@link RateLimitProperties.RouteConfig#getRoutes()} path 维度配置业务规则
     *       （VIP 客户不限流 / 普通用户 5 req/s / 敏感接口 1 req/s），与反压防护正交、各管一段。</li>
     * </ul>
     */
    @NestedConfigurationProperty
    private RateLimitProperties rateLimit = new RateLimitProperties();

    /**
     * 业务接口级熔断子域。前缀：{@code platform.component.web.circuit-breaker}。
     * <p><strong style="color:#c00">职责定位（绝对断言）</strong>：本子域<strong>仅承载业务接口级熔断</strong>，
     * <strong>永远不承担反压保护职责</strong>。
     * <ul>
     *   <li><strong>反压保护</strong>必须由容器线程池<strong>之前</strong>的层处理：
     *       <ul>
     *         <li>部署 gateway 时：gateway {@code SecurityFilter}（IP / UA 维度熔断）</li>
     *         <li>不部署 gateway 时：<strong>Sentinel MVC 适配器</strong>（容器线程池侧）</li>
     *       </ul>
     *   </li>
     *   <li><strong>为什么不在这里做反压？</strong>运行在 Servlet 容器线程池<strong>之后</strong>——大流量进入时
     *       容器已经先挂，运行在后面的熔断器没机会救场。</li>
     *   <li><strong>本子域的正确用法</strong>：按 {@link RateLimitProperties.RouteConfig#getRoutes()} path 维度隔离独立 CB 状态机
     *       （"订单查询"失败率 30% 熔断保护下游 DB，"用户查询"不受影响），属于<strong>业务资源保护</strong>。</li>
     * </ul>
     */
    @NestedConfigurationProperty
    private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();

    /**
     * 过滤器通用配置（KeyResolver header 等）。前缀：{@code platform.component.web.filter}。
     * <p><strong>Gateway 模式下保留但需对齐</strong>：gateway 端 {@code SecurityFilterConfig}
     * 会透传 client header 到 web 端（如 {@code X-Client-Id}），本子域 {@link WebFilterProperties#getKeyHeader()}
     * 必须<strong>与 gateway 透传的 header 名一致</strong>，否则 RateLimit / CircuitBreaker 拦截器拿不到 clientKey。
     */
    @NestedConfigurationProperty
    private WebFilterProperties filter = new WebFilterProperties();

    /**
     * 请求卡死检测子域（"hang" = 请求挂起 / 长时间不返回）。
     * <p>
     * 拦截器链中识别耗时超过阈值的请求，按三档阈值（warn / dump / kill）自动告警、dump 线程栈、
     * 极端情况下中断业务线程。前缀：{@code platform.component.web.hang}。
     * <p><strong>可与 Gateway 共存</strong>：gateway 有 request timeout（看总耗时），但本子域关注拦截器链
     * 内单方法耗时 + 线程栈 dump + 三档分级处置，<strong>不同维度</strong>，gateway 部署时仍可启用。
     */
    @NestedConfigurationProperty
    private HangDetectionProperties hang = new HangDetectionProperties();

    /**
     * 业务接口级降级子域。前缀：{@code platform.component.web.degrade}。
     * <p><strong style="color:#c00">职责定位（绝对断言）</strong>：本子域<strong>仅承载业务接口级降级响应</strong>，
     * <strong>永远不承担上游兜底职责</strong>。
     * <ul>
     *   <li><strong>上游兜底</strong>（上游不可达 / 超时的统一 fallback）必须由容器线程池<strong>之前</strong>的层处理：
     *       <ul>
     *         <li>部署 gateway 时：gateway {@code FallbackConfig}</li>
     *         <li>不部署 gateway 时：<strong>Sentinel MVC 适配器</strong>（容器线程池侧）或
     *             Web 容器本身（tomcat / jetty 自带的 error page）</li>
     *       </ul>
     *   </li>
     *   <li><strong>为什么不在这里做上游兜底？</strong>运行在 Servlet 容器线程池<strong>之后</strong>——上游不可达时
     *       容器已经先报错/超时，运行在后面的降级拿不到完整上下文。</li>
     *   <li><strong>本子域的正确用法</strong>：按 {@link RateLimitProperties.RouteConfig#getRoutes()} path 维度返回业务特定的 code/msg
     *       （"订单查询"返回 {@code ORDER_DEGRADED}，"用户查询"返回 {@code USER_DEGRADED}，
     *       或引用 {@code fallbackBean} 动态生成响应），属于<strong>业务响应差异化</strong>。</li>
     * </ul>
     */
    @NestedConfigurationProperty
    private DegradeProperties degrade = new DegradeProperties();

    /**
     * 平台防护层子域。前缀：{@code platform.component.web.protection}。
* <p><strong>Gateway 模式下需按子项区分</strong>：
 * <ul>
 *   <li>{@code request-size}：gateway 层 body size 限制已生效，<strong>建议禁用</strong></li>
 * </ul>
     */
    @NestedConfigurationProperty
    private PlatformProtectionProperties protection = new PlatformProtectionProperties();

    /**
     * 业务能力集成子域。前缀：{@code platform.component.web.business}。
     * <p><strong>Gateway 模式下需按子项区分</strong>：
     * <ul>
     *   <li>{@code tenant}：gateway 端 {@code TenantFilter} 已解析，<strong>建议禁用</strong>（重复解析 + gateway 已规范化 {@code X-Tenant-Id}）</li>
     *   <li>{@code idempotency}：gateway 端 {@code DuplicateSubmitConfig} 已检查，<strong>建议禁用</strong>（双层检查易误判）</li>
     *   <li>{@code api-version}：gateway 不解析，<strong>可独立启用</strong>（controller 路由级版本协商）</li>
     * </ul>
     */
    @NestedConfigurationProperty
    private BusinessIntegrationProperties business = new BusinessIntegrationProperties();
}