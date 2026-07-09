/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.web.core.config.protection;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 平台防护层配置（前缀：{@code platform.component.web.protection}，README.md §4.8）。
 * <p>
 * 控制 {@link com.richie.component.web.core.protection.PlatformProtectionInterceptor} /
 * {@link com.richie.component.web.core.protection.AnomalyDetectionInterceptor} /
 * {@link com.richie.component.web.core.protection.BruteForceInterceptor} 的兜底响应。
 *
 * <h2>子配置</h2>
 * <ul>
 *   <li>{@link RequestSize}：请求体 / header 字节超限时的兜底响应</li>
 *   <li>{@link AnomalyDetection}：Bot UA / IP 黑名单拦截的兜底响应</li>
 *   <li>{@link BruteForce}：登录防爆破的兜底响应</li>
 *   <li>{@link LongLived}：长连接旁路路径（{@code /sse/**} / {@code /ws/**} 等），命中后
 *       {@code PlatformProtectionInterceptor} 在 ctx 上设 {@code long-lived=true}，
 *       下游 {@code HangDetectionInterceptor} 据此跳过 watchdog 注册</li>
 * </ul>
 *
 * <h2>配置驱动（铁律）</h2>
 * <p>每个子配置都有 {@code enabled} 字段（默认 {@code true}），由
 * {@link PlatformProtectionAutoConfiguration} 的 {@code @ConditionalOnProperty}
 * 消费——{@code enabled=false} → 对应 {@code @Bean} 不创建 → 拦截器链不包含。
 *
 * <h2 style="color:#c00">⚠ Gateway 模式：按子项分别处置</h2>
 * <table border="1">
 *   <caption>平台防护子项 vs Gateway 职责对照</caption>
 *   <tr><th>子项</th><th>Gateway 是否已接管</th><th>gateway 模式下处置</th></tr>
 *   <tr>
 *     <td>{@link RequestSize}</td>
 *     <td>✅ gateway 层有 body-size / header-size 限制</td>
 *     <td><strong>建议禁用</strong>：双层限制功能重复</td>
 *   </tr>
 *   <tr>
 *     <td>{@link AnomalyDetection}</td>
 *     <td>部分（WAF 已有 UA 黑名单，但 IP 黑名单通常 web 层做）</td>
 *     <td>独立部署建议启用；gateway 部署时如 WAF 已覆盖可禁用</td>
 *   </tr>
 *   <tr>
 *     <td>{@link BruteForce}</td>
 *     <td>❌ gateway 不感知登录失败计数</td>
 *     <td>✅ 可独立启用</td>
 *   </tr>
 * </table>
 *
 * @author richie696
 * @since 2026-07
 */
@Data
@ConfigurationProperties(prefix = "platform.component.web.protection")
public class PlatformProtectionProperties {

    /** 全局防护开关（false → 所有子拦截器不创建）。默认 true。 */
    private boolean enabled = true;

    /** RequestSizeGuard 子段。 */
    private RequestSize requestSize = new RequestSize();

    /** AnomalyDetection 子段。 */
    private AnomalyDetection anomalyDetection = new AnomalyDetection();

    /** BruteForce 子段。 */
    private BruteForce bruteForce = new BruteForce();

    /** LongLived 路径旁路子段。 */
    private LongLived longLived = new LongLived();

    /**
     * RequestSize 兜底响应（请求体 / header 字节超限时触发）。
     */
    @Data
    public static class RequestSize {
        /** 子开关（false → RequestSizeGuard 不创建）。默认 true。 */
        private boolean enabled = true;
        /** 单请求 body 最大字节数，默认 1 MiB。 */
        private long maxBodyBytes = 1024L * 1024L;
        /** 单请求 header 总字节数上限，默认 8 KiB。 */
        private long maxHeaderBytes = 8L * 1024L;
        /** body 超限时响应状态码，默认 413 (Payload Too Large)。 */
        private int bodyDenyStatus = 413;
        /** header 超限时响应状态码，默认 431 (Request Header Fields Too Large)。 */
        private int headerDenyStatus = 431;
        /** 兜底业务 code。 */
        private String denyCode = "REQUEST_TOO_LARGE";
        /** 兜底业务 msg，支持占位符 {@code {reason}} / {@code {limit}} / {@code {actual}}。 */
        private String denyMsg = "请求体过大 ({reason}, limit={limit}B, actual={actual}B)";
    }

    /**
     * AnomalyDetection 兜底响应（Bot UA / IP 黑名单命中时触发）。
     */
    @Data
    public static class AnomalyDetection {
        /** 子开关。默认 true。 */
        private boolean enabled = true;
        /** Bot UA 模式列表（Ant-style 通配），如 {@code ["curl/*", "wget/*"]}。 */
        private List<String> botUserAgents = new ArrayList<>();
        /** IP 黑名单（CIDR 或精确 IP），命中即拒绝。 */
        private List<String> ipBlacklist = new ArrayList<>();
        /** 兜底 HTTP 状态码；默认 403。 */
        private int denyStatus = 403;
        /** 兜底业务 code。 */
        private String denyCode = "BOT_DETECTED";
        /** 兜底业务 msg，支持占位符 {@code {ua}} / {@code {ip}}。 */
        private String denyMsg = "检测到异常客户端 ({ua}, ip={ip})";
    }

    /**
     * BruteForce 兜底响应（同一 {@code username} 在窗口内失败超阈值 → 锁定）。
     */
    @Data
    public static class BruteForce {
        /** 子开关。默认 true。 */
        private boolean enabled = true;
        /** 失败计数窗口（秒）。 */
        private long windowSeconds = 60L;
        /** 窗口内允许的最大失败次数。 */
        private int maxAttempts = 5;
        /** 锁定时长（秒）。 */
        private long lockoutSeconds = 60L;
        /** 兜底 HTTP 状态码；默认 429。 */
        private int denyStatus = 429;
        /** 兜底业务 code。 */
        private String denyCode = "BRUTE_FORCE";
        /** 兜底业务 msg，支持占位符 {@code {lockout}}。 */
        private String denyMsg = "登录尝试过于频繁，已锁定 {lockout} 秒";
    }

    /**
     * LongLived 路径旁路（命中后在 ctx 上设 {@code long-lived=true}，HangDetection 据此跳过 watchdog）。
     */
    @Data
    public static class LongLived {
        /** Ant-style 路径模式列表，默认 {@code ["/sse/**", "/ws/**"]}。 */
        private List<String> pathPatterns = List.of("/sse/**", "/ws/**");
    }
}