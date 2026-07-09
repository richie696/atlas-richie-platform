/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.web.core.config.business;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 业务能力集成配置（前缀：{@code platform.component.web.business}，README.md §4.9）。
 * <p>
 * 控制业务相关拦截器（Tenant / Idempotency / ApiVersion）的开关 / 兜底响应 / header 解析。
 *
 * <h2>子配置</h2>
 * <ul>
 *   <li>{@link Tenant}：多租户解析（§4.9）</li>
 *   <li>{@link Idempotency}：幂等性校验</li>
 *   <li>{@link ApiVersion}：API 版本协商</li>
 * </ul>
 *
 * <h2 style="color:#c00">⚠ Gateway 模式下的处置（与 atlas-richie-gateway-service 职责对照）</h2>
 * <p>三个子项对 gateway 的关系<strong>不统一</strong>，需按子项分别处置：
 * <table border="1">
 *   <caption>业务子项 vs Gateway 职责对照（按子项分别处置）</caption>
 *   <tr><th>子项</th><th>Gateway 是否已接管</th><th>gateway 模式下处置</th></tr>
 *   <tr>
 *     <td>{@link Tenant}</td>
 *     <td>✅ {@code TenantFilter} 已解析（gateway 已规范化 {@code X-Tenant-Id}）</td>
 *     <td><strong>建议禁用</strong>：整体 tenant 段留空或显式关掉；web 端再解析属于重复劳动</td>
 *   </tr>
 *   <tr>
 *     <td>{@link Idempotency}</td>
 *     <td>✅ {@code DuplicateSubmitConfig} 已检查</td>
 *     <td><strong>建议禁用</strong>：双层幂等检查易误判（gateway 端判定时间内 web 端看不到）</td>
 *   </tr>
 *   <tr>
 *     <td>{@link ApiVersion}</td>
 *     <td>❌ gateway 不解析</td>
 *     <td>✅ 可独立启用：版本协商是 controller 路由级职责，gateway 不干预</td>
 *   </tr>
 * </table>
 *
 * @author richie696
 * @since 2026-07
 */
@Data
@ConfigurationProperties(prefix = "platform.component.web.business")
public class BusinessIntegrationProperties {

    private Tenant tenant = new Tenant();
    private Idempotency idempotency = new Idempotency();
    private ApiVersion apiVersion = new ApiVersion();

    /**
     * 多租户解析配置。
     * <p><strong style="color:#c00">Gateway 模式下建议禁用</strong>：gateway 端 {@code TenantFilter} 已解析
     * {@code X-Tenant-Id} 并规范化，web 端 {@code TenantInterceptor} 再做一次属于重复解析 + 浪费。
     * 部署 gateway 时建议本子段留空。
     */
    @Data
    public static class Tenant {
        /**
         * 租户 ID header 名。默认 {@code X-Tenant-Id}。
         */
        private String headerName = "X-Tenant-Id";
        /**
         * 缺失租户 header 时是否要求拒绝（true → 400 short-circuit）。默认 false（放行）。
         */
        private boolean requireOnMissing = false;
    }

    /**
     * 幂等性校验配置。
     * <p><strong style="color:#c00">Gateway 模式下建议禁用</strong>：gateway 端 {@code DuplicateSubmitConfig}
     * 已基于 Redis 做幂等检查，web 端 {@code IdempotencyInterceptor} 再做一次会因<strong>窗口不可见</strong>
     * （gateway 端判定的请求 web 端看不到）导致误判。部署 gateway 时建议本子段留空或 {@code deny-status=0}。
     */
    @Data
    public static class Idempotency {
        /**
         * 幂等 key header 名（业务方可指定如 {@code X-Idempotency-Key}）。默认 {@code X-Idempotency-Key}。
         */
        private String headerName = "X-Idempotency-Key";
        /**
         * 幂等校验失败（重复请求）兜底 HTTP 状态码；默认 409。
         */
        private int denyStatus = 409;
        /**
         * 幂等校验失败兜底业务 code。默认 {@code idempotent_replay}（小写，与 {@link IdempotencyInterceptor} HookBus reason 一致）。
         */
        private String denyCode = "idempotent_replay";
        /**
         * 幂等校验失败兜底业务 msg。默认 {@code 检测到重复请求}。
         */
        private String denyMsg = "检测到重复请求";
    }

    /**
     * API 版本协商配置。
     * <p><strong style="color:#0a0">✅ 可与 Gateway 共存</strong>：版本协商是 controller 路由级职责
     * （根据版本走不同代码分支），gateway 不做版本解析，本子项可独立启用。
     */
    @Data
    public static class ApiVersion {
        /**
         * 版本 header 名（如 {@code X-Api-Version} 或 {@code Accept}）。默认 {@code X-Api-Version}。
         */
        private String headerName = "X-Api-Version";
        /**
         * 未带版本 header 时使用的默认版本。默认 {@code default}。
         */
        private String defaultVersion = "default";
        /**
         * 解析后写入 ctx.attribute 的 key（供下游业务读取）。默认 {@code apiVersion}。
         */
        private String attributeKey = "apiVersion";
    }
}