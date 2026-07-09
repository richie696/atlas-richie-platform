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
package com.richie.component.mqtt.beans;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 网络质量事件
 * <p>
 * 用于表示网络质量监控的结果，包含延迟和丢包率信息。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-27
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NetworkQualityEvent {

    /**
     * Ping延迟时间（毫秒）
     */
    private Integer pingLatency;

    /**
     * 丢包率（百分比）
     */
    private Float packetLoss;
}
