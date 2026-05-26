package com.richie.component.mqtt.beans;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 网络质量统计信息
 * <p>
 * 包含网络质量的历史统计数据，用于评估网络连接质量。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetworkQualityStats {

    /**
     * 总Ping次数
     */
    private long totalPingCount;

    /**
     * 成功Ping次数
     */
    private long successfulPingCount;

    /**
     * 丢包数量
     */
    private long packetLossCount;

    /**
     * 平均延迟（毫秒）
     */
    private long averageLatency;

    /**
     * 丢包率（百分比）
     */
    private float packetLossRate;

    /**
     * 最近100次网络质量事件
     */
    private List<NetworkQualityEvent> recentQualityEvents;

    /**
     * 计算成功率
     *
     * @return 成功率（百分比），0-100之间的浮点数
     */
    public float getSuccessRate() {
        return totalPingCount > 0 ? (float) successfulPingCount / totalPingCount * 100 : 0;
    }

    /**
     * 获取网络质量等级
     *
     * @return 网络质量等级字符串（EXCELLENT/GOOD/FAIR/POOR）
     */
    public String getNetworkQualityLevel() {
        if (packetLossRate > 10 || averageLatency > 100) {
            return "POOR";
        } else if (packetLossRate > 5 || averageLatency > 50) {
            return "FAIR";
        } else if (packetLossRate > 1 || averageLatency > 20) {
            return "GOOD";
        } else {
            return "EXCELLENT";
        }
    }
}
