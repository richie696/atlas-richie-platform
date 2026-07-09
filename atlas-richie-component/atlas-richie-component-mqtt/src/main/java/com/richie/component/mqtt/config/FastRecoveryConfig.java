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
package com.richie.component.mqtt.config;


import lombok.Data;

/**
 * 快速恢复配置
 * <p>
 * 该配置类用于控制MQTT客户端的快速恢复机制，包括网络监控、连接状态监控、快速重连等功能。
 * 通过合理的配置可以显著提升MQTT客户端在网络不稳定环境下的连接稳定性和恢复速度。
 * <p>
 * <strong>推荐配置场景：</strong>
 * <ul>
 *   <li><strong>生产环境</strong>：启用所有监控功能，设置较短的检测间隔</li>
 *   <li><strong>开发环境</strong>：可以适当延长检测间隔，减少资源消耗</li>
 *   <li><strong>弱网环境</strong>：增加重连次数，延长超时时间</li>
 *   <li><strong>稳定网络</strong>：可以减少监控频率，优化性能</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-11 00:47:08
 */
@Data
public class FastRecoveryConfig {

    /**
     * 是否启用快速恢复模式
     *
     * <p>控制是否启用整个快速恢复机制。当设置为false时，将禁用所有快速恢复相关功能，
     * 包括网络监控、连接状态监控、快速重连等。
     *
     * <strong>推荐场景：</strong>
     * <ul>
     *   <li><strong>生产环境</strong>：true（推荐）</li>
     *   <li><strong>开发测试</strong>：true（推荐）</li>
     *   <li><strong>资源受限环境</strong>：false（可选）</li>
     * </ul>
     *
     * <strong>推荐值：</strong>
     * <ul>
     *   <li>默认值：true</li>
     *   <li>范围：true/false</li>
     * </ul>
     */
    private boolean enabled = true;

    /**
     * 网络检测间隔（秒）
     *
     * <p>网络监控器检测网络状态的频率。较短的间隔可以更快地检测到网络变化，
     * 但会增加系统资源消耗。
     *
     * <strong>推荐场景：</strong>
     * <ul>
     *   <li><strong>不稳定网络</strong>：3-5秒（快速响应）</li>
     *   <li><strong>稳定网络</strong>：5-10秒（平衡性能）</li>
     *   <li><strong>资源受限</strong>：10-30秒（减少消耗）</li>
     * </ul>
     *
     * <strong>推荐值：</strong>
     * <ul>
     *   <li>默认值：5秒</li>
     *   <li>范围：1-60秒</li>
     *   <li>最小值：1秒</li>
     * </ul>
     */
    private long networkCheckInterval = 5L;

    /**
     * 连接状态监控间隔（秒）
     *
     * <p>连接监控器检查MQTT连接状态的频率。用于检测连接是否仍然有效，
     * 及时发现连接断开或异常情况。
     *
     * <strong>推荐场景：</strong>
     * <ul>
     *   <li><strong>高可靠性要求</strong>：5-10秒（快速检测）</li>
     *   <li><strong>一般应用</strong>：10-20秒（平衡性能）</li>
     *   <li><strong>低频率应用</strong>：20-30秒（减少开销）</li>
     * </ul>
     *
     * <strong>推荐值：</strong>
     * <ul>
     *   <li>默认值：10秒</li>
     *   <li>范围：5-60秒</li>
     *   <li>最小值：5秒</li>
     * </ul>
     */
    private long connectionMonitorInterval = 10L;

    /**
     * 快速重连间隔（毫秒）
     *
     * <p>快速重连机制中每次重连尝试之间的等待时间。较短的间隔可以更快地恢复连接，
     * 但可能增加服务器压力。
     *
     * <strong>推荐场景：</strong>
     * <ul>
     *   <li><strong>临时网络抖动</strong>：500-1000毫秒（快速恢复）</li>
     *   <li><strong>一般网络环境</strong>：1000-2000毫秒（平衡策略）</li>
     *   <li><strong>服务器压力大</strong>：2000-5000毫秒（减轻压力）</li>
     * </ul>
     *
     * <strong>推荐值：</strong>
     * <ul>
     *   <li>默认值：1000毫秒</li>
     *   <li>范围：100-10000毫秒</li>
     *   <li>最小值：100毫秒</li>
     * </ul>
     */
    private long fastReconnectInterval = 1000L;

    /**
     * 最大快速重连次数
     *
     * <p>快速重连机制的最大尝试次数。达到此次数后，如果仍未连接成功，
     * 将触发完整的重新初始化流程。
     *
     * <strong>推荐场景：</strong>
     * <ul>
     *   <li><strong>临时网络问题</strong>：5-10次（快速尝试）</li>
     *   <li><strong>一般网络环境</strong>：10-15次（平衡策略）</li>
     *   <li><strong>不稳定网络</strong>：15-20次（更多尝试）</li>
     * </ul>
     *
     * <strong>推荐值：</strong>
     * <ul>
     *   <li>默认值：10次</li>
     *   <li>范围：1-50次</li>
     *   <li>最小值：1次</li>
     * </ul>
     */
    private int maxFastReconnectAttempts = 10;

    /**
     * 网络连接超时（毫秒）
     *
     * <p>网络连接建立的最大等待时间。用于检测网络是否可达，
     * 避免在网络不可用时长时间等待。
     *
     * <strong>推荐场景：</strong>
     * <ul>
     *   <li><strong>局域网环境</strong>：1000-3000毫秒（快速检测）</li>
     *   <li><strong>互联网环境</strong>：3000-5000毫秒（考虑延迟）</li>
     *   <li><strong>弱网环境</strong>：5000-10000毫秒（容忍延迟）</li>
     * </ul>
     *
     * <strong>推荐值：</strong>
     * <ul>
     *   <li>默认值：3000毫秒</li>
     *   <li>范围：1000-30000毫秒</li>
     *   <li>最小值：1000毫秒</li>
     * </ul>
     */
    private int networkConnectTimeout = 3000;

    /**
     * 心跳间隔（秒）- 快速检测断线（默认：30秒）
     * <p>
     * <strong>功能说明：</strong>
     * 控制MQTT客户端向服务器发送心跳包的时间间隔，用于检测连接状态。
     * <p>
     * <strong>影响范围：</strong>
     * <ul>
     *   <li>连接检测：服务器通过心跳检测客户端是否在线</li>
     *   <li>遗嘱消息：心跳超时后触发遗嘱消息发送</li>
     *   <li>断网检测：影响异常断网时的检测延迟</li>
     *   <li>网络资源：影响网络流量和服务器负载</li>
     * </ul>
     * <p>
     * <strong>参数说明：</strong>
     * <ul>
     *   <li><code>10</code>：快速检测，10秒内检测到断网</li>
     *   <li><code>15</code>：快速检测，15秒内检测到断网</li>
     *   <li><code>30</code>：标准检测，30秒内检测到断网（默认）</li>
     *   <li><code>60</code>：慢速检测，60秒内检测到断网</li>
     *   <li><code>90</code>：慢速检测，90秒内检测到断网</li>
     * </ul>
     * <p>
     * <strong>适用场景：</strong>
     * <ul>
     *   <li><code>10-15</code>：实时监控场景；需要快速检测设备离线</li>
     *   <li><code>30</code>：一般业务场景；平衡检测速度和网络负载</li>
     *   <li><code>60-90</code>：网络不稳定环境；减少心跳频率</li>
     * </ul>
     * <p>
     * <strong>推荐配置：</strong>
     * <ul>
     *   <li>KDS门店场景：<code>15</code>（快速检测门店断网）</li>
     *   <li>实时监控：<code>10</code>（最快检测）</li>
     *   <li>一般应用：<code>30</code>（标准配置）</li>
     *   <li>弱网环境：<code>60</code>（减少心跳频率）</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>心跳间隔越短，断网检测越快，但网络负载越高</li>
     *   <li>心跳间隔越长，网络负载越低，但断网检测越慢</li>
     *   <li>建议根据网络环境和业务需求平衡配置</li>
     * </ul>
     */
    private int keepAliveInterval = 30;



    /**
     * 是否启用网络监控
     *
     * <p>控制是否启用网络状态监控功能。启用后，系统会定期检测网络连接状态，
     * 在网络恢复时自动触发重连。
     *
     * <strong>推荐场景：</strong>
     * <ul>
     *   <li><strong>移动网络环境</strong>：true（推荐）</li>
     *   <li><strong>不稳定网络</strong>：true（推荐）</li>
     *   <li><strong>稳定网络</strong>：true（可选）</li>
     *   <li><strong>资源受限</strong>：false（减少开销）</li>
     * </ul>
     *
     * <strong>推荐值：</strong>
     * <ul>
     *   <li>默认值：true</li>
     *   <li>范围：true/false</li>
     * </ul>
     */
    private boolean enableNetworkMonitor = true;

    /**
     * 是否启用连接状态监控
     *
     * <p>控制是否启用MQTT连接状态监控功能。启用后，系统会定期检查MQTT连接的有效性，
     * 及时发现连接异常。
     *
     * <strong>推荐场景：</strong>
     * <ul>
     *   <li><strong>高可靠性要求</strong>：true（推荐）</li>
     *   <li><strong>一般应用</strong>：true（推荐）</li>
     *   <li><strong>资源受限</strong>：false（可选）</li>
     * </ul>
     *
     * <strong>推荐值：</strong>
     * <ul>
     *   <li>默认值：true</li>
     *   <li>范围：true/false</li>
     * </ul>
     */
    private boolean enableConnectionMonitor = true;

    /**
     * 是否启用快速重连
     *
     * <p>控制是否启用快速重连机制。启用后，在连接断开时会立即尝试重连，
     * 而不是等待完整的重新初始化流程。
     *
     * <strong>推荐场景：</strong>
     * <ul>
     *   <li><strong>临时网络问题</strong>：true（推荐）</li>
     *   <li><strong>快速恢复要求</strong>：true（推荐）</li>
     *   <li><strong>服务器压力大</strong>：false（可选）</li>
     * </ul>
     *
     * <strong>推荐值：</strong>
     * <ul>
     *   <li>默认值：true</li>
     *   <li>范围：true/false</li>
     * </ul>
     */
    private boolean enableFastReconnect = true;

    /**
     * 网络恢复后立即重连
     *
     * <p>控制是否在网络恢复后立即尝试重连。启用后，一旦检测到网络恢复，
     * 会立即触发重连流程，无需等待其他条件。
     *
     * <strong>推荐场景：</strong>
     * <ul>
     *   <li><strong>移动网络环境</strong>：true（推荐）</li>
     *   <li><strong>快速恢复要求</strong>：true（推荐）</li>
     *   <li><strong>网络切换频繁</strong>：true（推荐）</li>
     * </ul>
     *
     * <strong>推荐值：</strong>
     * <ul>
     *   <li>默认值：true</li>
     *   <li>范围：true/false</li>
     * </ul>
     */
    private boolean reconnectOnNetworkRecovery = true;

    /**
     * 连接断开后立即重连
     *
     * <p>控制是否在连接断开后立即尝试重连。启用后，一旦检测到连接断开，
     * 会立即启动重连流程，无需等待其他触发条件。
     *
     * <strong>推荐场景：</strong>
     * <ul>
     *   <li><strong>高可用性要求</strong>：true（推荐）</li>
     *   <li><strong>快速恢复要求</strong>：true（推荐）</li>
     *   <li><strong>服务器压力大</strong>：false（可选）</li>
     * </ul>
     *
     * <strong>推荐值：</strong>
     * <ul>
     *   <li>默认值：true</li>
     *   <li>范围：true/false</li>
     * </ul>
     */
    private boolean reconnectOnDisconnect = true;
}
