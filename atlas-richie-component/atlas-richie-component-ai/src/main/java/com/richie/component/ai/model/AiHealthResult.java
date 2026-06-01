package com.richie.component.ai.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 模型健康检查结果
 */
@Data
@Accessors(chain = true)
public class AiHealthResult {

    private String modelName;

    private String provider;

    private boolean healthy;

    private boolean liveProbe;

    private Long durationMs;

    private String message;

    private LocalDateTime checkedAt;

    public static AiHealthResult healthy(String modelName, String provider, boolean liveProbe, long durationMs) {
        return new AiHealthResult()
                .setModelName(modelName)
                .setProvider(provider)
                .setHealthy(true)
                .setLiveProbe(liveProbe)
                .setDurationMs(durationMs)
                .setMessage("OK")
                .setCheckedAt(LocalDateTime.now());
    }

    public static AiHealthResult unhealthy(String modelName, String provider, boolean liveProbe, String message) {
        return new AiHealthResult()
                .setModelName(modelName)
                .setProvider(provider)
                .setHealthy(false)
                .setLiveProbe(liveProbe)
                .setMessage(message)
                .setCheckedAt(LocalDateTime.now());
    }
}
