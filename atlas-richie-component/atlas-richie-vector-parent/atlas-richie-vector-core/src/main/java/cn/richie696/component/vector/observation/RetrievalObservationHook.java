/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.observation;

/**
 * Vector 检索阶段观测钩子。
 *
 * <p>默认是 no-op，未安装观测实现时不增加数据库、网络或线程池依赖。
 * 实现必须尽快返回且不应抛异常；Vector Core 会通过 {@link #safeEmit} 隔离观测故障，
 * 观测系统不可影响检索主链路。</p>
 */
@FunctionalInterface
public interface RetrievalObservationHook {

    RetrievalObservationHook NOOP = event -> { };

    void onEvent(RetrievalObservationEvent event);

    static RetrievalObservationHook noOp() {
        return NOOP;
    }

    static void safeEmit(RetrievalObservationHook hook, RetrievalObservationEvent event) {
        if (hook == null || hook == NOOP || event == null) return;
        try {
            hook.onEvent(event);
        } catch (RuntimeException ignored) {
            // 观测故障不能改变检索结果或触发链路；实现方可在自身日志中记录异常。
        }
    }
}
