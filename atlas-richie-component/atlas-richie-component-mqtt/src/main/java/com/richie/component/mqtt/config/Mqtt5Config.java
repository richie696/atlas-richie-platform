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
package com.richie.component.mqtt.config;

import lombok.Data;

/**
 * MQTT 5.0 专用配置
 * <p>
 * 本配置类提供了MQTT 5.0协议的所有高级特性配置选项，
 * 包括会话管理、消息可靠性、遗嘱消息、用户属性等功能。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-11 00:47:35
 */
@Data
public class Mqtt5Config {

    /**
     * 客户端类型标识
     * <p>
     * <strong>功能说明：</strong>
     * 标识客户端的类型，用于服务器端进行分类处理、权限控制和客户端识别。
     * <p>
     * <strong>推荐值：</strong>
     * <ul>
     *   <li><code>pos_terminal</code>：POS收银终端</li>
     *   <li><code>kds_box</code>：KDS盒子终端</li>
     *   <li><code>kds_screen</code>：KDS屏</li>
     *   <li><code>kds_server</code>：KDS服务端</li>
     * </ul>
     * 其它所有具备具体意义的名词都可以。
     */
    private String clientType = "pos_terminal";

    /**
     * 是否保持会话状态
     * <p>
     * <strong>功能说明：</strong>
     * 控制MQTT连接是否使用持久会话。启用后，服务器会保存客户端的订阅信息，
     * 在重连时自动恢复订阅状态。此配置对应MQTT 5.0的cleanStart参数（取反）。
     * <p>
     * <strong>配置映射关系：</strong>
     * <ul>
     *   <li><code>keepSession = true</code> ↔ <code>cleanStart = false</code>（保持会话）</li>
     *   <li><code>keepSession = false</code> ↔ <code>cleanStart = true</code>（清除会话）</li>
     * </ul>
     * <p>
     * <strong>推荐场景：</strong>
     * <ul>
     *   <li><strong>需要消息可靠性</strong>：true（推荐）</li>
     *   <li><strong>临时连接</strong>：false（可选）</li>
     *   <li><strong>资源受限</strong>：false（减少服务器负载）</li>
     *   <li><strong>EMQX服务器</strong>：false（避免会话冲突）</li>
     * </ul>
     * <p>
     * <strong>推荐配置：</strong>
     * <ul>
     *   <li>KDS门店场景：<code>true</code>（保证消息不丢失）</li>
     *   <li>EMQX服务器：<code>false</code>（避免会话冲突）</li>
     * </ul>
     */
    private boolean keepSession = true;

    /**
     * 会话过期时间（秒）
     * <p>
     * <strong>功能说明：</strong>
     * 控制MQTT会话在客户端断开后的保持时间。从客户端断开连接开始计时。
     * <p>
     * <strong>影响范围：</strong>
     * <ul>
     *   <li>订阅关系：会话期间内重连可恢复订阅</li>
     *   <li>未确认消息：QoS 1/2的未确认消息会保留</li>
     *   <li>遗嘱消息：会话期间内遗嘱消息不会发送</li>
     *   <li>会话状态：服务器端会话信息</li>
     * </ul>
     * <p>
     * <strong>参数说明：</strong>
     * <ul>
     *   <li><code>0</code>：会话立即过期，每次连接都是新会话</li>
     *   <li><code>300</code>：会话保持5分钟</li>
     *   <li><code>1800</code>：会话保持30分钟</li>
     *   <li><code>3600</code>：会话保持1小时</li>
     *   <li><code>7200</code>：会话保持2小时</li>
     * </ul>
     * <p>
     * <strong>适用场景：</strong>
     * <ul>
     *   <li><code>0</code>：EMQX服务器兼容性；避免会话冲突</li>
     *   <li><code>300-1800</code>：网络不稳定环境；需要消息可靠性</li>
     *   <li><code>3600+</code>：长时间断网场景；高可靠性要求</li>
     * </ul>
     * <p>
     * <strong>推荐配置：</strong>
     * <ul>
     *   <li>KDS门店场景：<code>1800</code>（30分钟，保证消息不丢失）</li>
     *   <li>EMQX服务器：<code>0</code>（立即过期，避免冲突）</li>
     * </ul>
     */
    private long sessionExpiryInterval = 1800L;

    /**
     * 消息过期时间（秒）
     * <p>
     * <strong>功能说明：</strong>
     * 控制单个消息在服务器端的保留时间。从消息到达服务器开始计时。
     * <p>
     * <strong>影响范围：</strong>
     * <ul>
     *   <li>已发布消息：服务器端消息的保留时间</li>
     *   <li>消息时效性：过期消息不会被投递给订阅者</li>
     *   <li>服务器资源：影响服务器内存和存储使用</li>
     * </ul>
     * <p>
     * <strong>参数说明：</strong>
     * <ul>
     *   <li><code>60</code>：消息1分钟内有效</li>
     *   <li><code>300</code>：消息5分钟内有效</li>
     *   <li><code>1800</code>：消息30分钟内有效</li>
     *   <li><code>3600</code>：消息1小时内有效</li>
     *   <li><code>0</code>：消息无过期时间限制</li>
     * </ul>
     * <p>
     * <strong>适用场景：</strong>
     * <ul>
     *   <li><code>60-300</code>：实时通知（打印通知、叫号通知）</li>
     *   <li><code>1800-3600</code>：重要通知（备餐通知、呈递通知）</li>
     *   <li><code>0</code>：配置信息、系统消息</li>
     * </ul>
     * <p>
     * <strong>推荐配置：</strong>
     * <ul>
     *   <li>打印通知：<code>300</code>（5分钟）</li>
     *   <li>备餐通知：<code>1800</code>（30分钟）</li>
     *   <li>呈递通知：<code>1800</code>（30分钟）</li>
     *   <li>叫号通知：<code>600</code>（10分钟）</li>
     * </ul>
     */
    private long messageExpiryInterval = 1800L;



    /**
     * 是否启用遗嘱消息
     * <p>
     * <strong>功能说明：</strong>
     * 控制客户端异常断开时是否发送遗嘱消息给服务器。
     * <p>
     * <strong>应用场景：</strong>
     * <ul>
     *   <li>设备状态监控：服务器可及时获知设备离线</li>
     *   <li>业务状态同步：门店离线时通知相关业务系统</li>
     *   <li>故障诊断：帮助分析设备离线原因</li>
     * </ul>
     * <p>
     * <strong>推荐配置：</strong>
     * <ul>
     *   <li>KDS门店场景：<code>true</code>（需要监控门店状态）</li>
     *   <li>服务端应用：<code>false</code>（服务端不需要遗嘱消息）</li>
     * </ul>
     */
    private boolean enableWillMessage = true;

    /**
     * 遗嘱消息主题
     * <p>
     * <strong>功能说明：</strong>
     * 遗嘱消息发布的主题，支持占位符替换。
     * <p>
     * <strong>占位符说明：</strong>
     * <ul>
     *   <li><code>{clientId}</code>：客户端ID</li>
     *   <li><code>{storeId}</code>：门店ID（如果配置了storeId）</li>
     *   <li><code>{timestamp}</code>：当前时间戳</li>
     *   <li><code>{networkType}</code>：网络类型（PUBLIC/VPC）</li>
     * </ul>
     * <p>
     * <strong>主题设计建议：</strong>
     * <ul>
     *   <li><code>device/status/{clientId}</code>：设备状态通知</li>
     *   <li><code>store/{storeId}/offline</code>：门店离线通知</li>
     *   <li><code>kds/store/{storeId}/status</code>：KDS门店状态</li>
     * </ul>
     * <p>
     * <strong>推荐配置：</strong>
     * <ul>
     *   <li>KDS门店：<code>kds/store/{clientId}/offline</code></li>
     *   <li>通用设备：<code>device/status/{clientId}</code></li>
     * </ul>
     */
    private String willTopic = "device/status/{clientId}";

    /**
     * 遗嘱消息内容
     * <p>
     * <strong>功能说明：</strong>
     * 遗嘱消息的具体内容，支持占位符替换。
     */
    private String willMessage = "异常断开连接";

    /**
     * 遗嘱消息上报的App版本号
     * <p>
     * <strong>功能说明：</strong>
     * 在遗嘱消息中包含应用版本信息，用于版本管理和问题诊断。
     * <p>
     * <strong>应用场景：</strong>
     * <ul>
     *   <li>版本兼容性检查</li>
     *   <li>问题诊断和版本关联</li>
     *   <li>升级策略制定</li>
     * </ul>
     * <p>
     * <strong>推荐配置：</strong>
     * <ul>
     *   <li>开发环境：<code>dev-1.0.0</code></li>
     *   <li>测试环境：<code>test-1.0.0</code></li>
     *   <li>生产环境：<code>1.0.0</code></li>
     * </ul>
     */
    private String appVersion = "";

    /**
     * 门店ID（可选参数）
     * <p>
     * <strong>功能说明：</strong>
     * 当配置以后，遗嘱消息和用户属性中都会包含该参数，
     * 以便服务端对终端进行业务处理。服务端无需设置，仅门店端需设置。
     * <p>
     * <strong>应用场景：</strong>
     * <ul>
     *   <li>门店状态监控</li>
     *   <li>业务数据关联</li>
     *   <li>权限控制</li>
     *   <li>消息路由</li>
     * </ul>
     * <p>
     * <strong>配置建议：</strong>
     * <ul>
     *   <li>格式：<code>STORE_001</code></li>
     *   <li>唯一性：确保门店ID唯一</li>
     *   <li>可读性：使用有意义的标识</li>
     * </ul>
     */
    private String storeId = "";

    /**
     * 是否启用用户属性
     * <p>
     * <strong>功能说明：</strong>
     * 控制是否在连接时发送自定义用户属性。
     * <p>
     * <strong>用户属性用途：</strong>
     * <ul>
     *   <li>连接标识和分类</li>
     *   <li>网络环境信息</li>
     *   <li>调试和监控</li>
     *   <li>业务逻辑标识</li>
     *   <li>负载均衡和路由</li>
     * </ul>
     * <p>
     * <strong>推荐配置：</strong>
     * <ul>
     *   <li>KDS门店：<code>true</code>（需要业务标识）</li>
     *   <li>服务端：<code>true</code>（需要连接管理）</li>
     * </ul>
     */
    private boolean enableUserProperties = true;



}
