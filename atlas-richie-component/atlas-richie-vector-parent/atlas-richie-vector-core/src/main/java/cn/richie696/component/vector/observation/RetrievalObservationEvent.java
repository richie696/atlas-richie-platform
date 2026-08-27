/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.observation;

import java.time.Duration;

/**
 * 一个检索阶段完成（或失败）后的观测事件。
 *
 * <p>{@code executed=false} 表示该阶段因未配置、候选不足或开关关闭而被跳过，
 * 不能将其耗时解释为真实 provider 调用耗时。</p>
 */
public record RetrievalObservationEvent(RetrievalObservationContext context,
                                        RetrievalStage stage,
                                        Duration elapsed,
                                        int inputCount,
                                        int outputCount,
                                        boolean executed,
                                        boolean applied,
                                        boolean success,
                                        String errorType) {
    public RetrievalObservationEvent {
        if (context == null) throw new IllegalArgumentException("context must not be null");
        if (stage == null) throw new IllegalArgumentException("stage must not be null");
        if (elapsed == null || elapsed.isNegative()) throw new IllegalArgumentException("elapsed must not be negative");
        if (inputCount < 0 || outputCount < 0) throw new IllegalArgumentException("counts must not be negative");
        if (success && errorType != null && !errorType.isBlank()) {
            throw new IllegalArgumentException("successful event must not carry errorType");
        }
    }

    public static RetrievalObservationEvent success(RetrievalObservationContext context, RetrievalStage stage,
                                                    Duration elapsed, int inputCount, int outputCount,
                                                    boolean applied) {
        return new RetrievalObservationEvent(context, stage, elapsed, inputCount, outputCount,
                true, applied, true, null);
    }

    public static RetrievalObservationEvent skipped(RetrievalObservationContext context, RetrievalStage stage,
                                                    int inputCount, int outputCount) {
        return new RetrievalObservationEvent(context, stage, Duration.ZERO, inputCount, outputCount,
                false, false, true, null);
    }

    public static RetrievalObservationEvent failure(RetrievalObservationContext context, RetrievalStage stage,
                                                    Duration elapsed, int inputCount, Throwable error) {
        String errorType = error == null ? "Unknown" : error.getClass().getName();
        return new RetrievalObservationEvent(context, stage, elapsed, inputCount, 0,
                true, false, false, errorType);
    }
}
