/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.observation;

import java.time.Duration;
import java.util.Map;

/** 一次检索的阶段观测快照。 */
public record RetrievalObservationSnapshot(RetrievalObservationContext context,
                                           Map<RetrievalStage, RetrievalObservationEvent> stages) {
    public RetrievalObservationSnapshot {
        if (context == null) throw new IllegalArgumentException("context must not be null");
        stages = Map.copyOf(stages == null ? Map.of() : stages);
    }

    public RetrievalObservationEvent event(RetrievalStage stage) {
        return stages.get(stage);
    }

    /** 只为真实执行且成功的阶段返回耗时；跳过/失败返回 null。 */
    public Duration measuredElapsed(RetrievalStage stage) {
        RetrievalObservationEvent event = event(stage);
        return event != null && event.executed() && event.success() ? event.elapsed() : null;
    }

    public boolean applied(RetrievalStage stage) {
        RetrievalObservationEvent event = event(stage);
        return event != null && event.applied();
    }
}
