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
