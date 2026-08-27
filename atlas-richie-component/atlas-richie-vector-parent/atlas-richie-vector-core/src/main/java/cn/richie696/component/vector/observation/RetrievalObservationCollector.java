/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.observation;

import java.util.EnumMap;
import java.util.Map;

/**
 * 单次检索使用的轻量内存 collector。
 *
 * <p>它只在调用线程内聚合阶段事件，调用方应在检索结束后读取 {@link #snapshot()}，
 * 再决定是否写入指标系统。</p>
 */
public final class RetrievalObservationCollector implements RetrievalObservationHook {
    private final RetrievalObservationContext context;
    private final Map<RetrievalStage, RetrievalObservationEvent> stages = new EnumMap<>(RetrievalStage.class);

    public RetrievalObservationCollector(RetrievalObservationContext context) {
        if (context == null) throw new IllegalArgumentException("context must not be null");
        this.context = context;
    }

    @Override
    public synchronized void onEvent(RetrievalObservationEvent event) {
        if (!context.operationId().equals(event.context().operationId())) return;
        stages.put(event.stage(), event);
    }

    public synchronized RetrievalObservationSnapshot snapshot() {
        return new RetrievalObservationSnapshot(context, stages);
    }
}
