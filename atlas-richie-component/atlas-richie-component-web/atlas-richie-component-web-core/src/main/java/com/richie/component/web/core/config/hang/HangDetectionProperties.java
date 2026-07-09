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
package com.richie.component.web.core.config.hang;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 请求卡死检测配置（前缀：{@code platform.component.web.hang}，README.md §4.4）。
 *
 * <h2>阈值链分级</h2>
 * <p>{@link #thresholdMillis} 保留为单一阈值的"快速配置"（等价于 {@link #warnMs}）；同时支持 3 档精细配置：
 * <ul>
 *   <li>{@link #warnMs}：超时仅 warn 日志 + publish HangEvent(level=WARN)</li>
 *   <li>{@link #dumpMs}：额外 thread-dump + publish HangEvent(level=DUMP)</li>
 *   <li>{@link #killSwitchMs}：额外 interrupt 业务线程（触发上游短路）+
 *       publish HangEvent(level=KILL_SWITCH)</li>
 * </ul>
 *
 * <p>三档必须满足 {@code warnMs ≤ dumpMs ≤ killSwitchMs}。不满足时构造器校验抛错。
 *
 * <h2 style="color:#0a0">✅ 可与 Gateway 共存（不同维度，不冲突）</h2>
 * <p>atlas-richie-gateway-service 端虽然也有 request timeout（粗粒度，丢包超时），
 * 但<strong>看的是"端到端网络/上游耗时"</strong>，无法定位到 web 服务的某个具体方法。
 * <p>本子域的 Hang 检测<strong>看的是"web 拦截器链 + 业务方法耗时"</strong>，能精确 dump 业务线程栈、上报
 * {@link com.richie.component.web.core.hang.HangEvent} 给告警系统。两套检测<strong>维度互补，不重叠</strong>。
 * <p>Gateway 模式下建议保留并独立配置（甚至可以比独立部署时更激进，比如 {@code warnMs=10s}）。
 *
 * @author richie696
 * @since 2026-07
 */
@Data
@ConfigurationProperties(prefix = "platform.component.web.hang")
public class HangDetectionProperties {

    /**
     * 是否启用 hang 检测拦截器；默认 true。
     */
    private boolean enabled = true;

    /**
     * 单一阈值（毫秒）。等价于 {@link #warnMs}——保留以便旧 yml 直接配置。
     */
    private long thresholdMillis = 30000L;

    /**
     * 第 1 档阈值（毫秒）：超时仅日志 + 事件。默认 {@link #thresholdMillis}。
     */
    private long warnMs = 30000L;

    /**
     * 第 2 档阈值（毫秒）：额外 thread-dump。默认 {@code warnMs + 10000}（向后兼容）。
     */
    private long dumpMs = 40000L;

    /**
     * 第 3 档阈值（毫秒）：额外 interrupt 业务线程。默认 {@code warnMs + 60000}（向后兼容）。
     */
    private long killSwitchMs = 90000L;

    /**
     * 是否 dump 线程栈（dump 档及以上触发）。默认 true。
     */
    private boolean dumpEnabled = true;
}