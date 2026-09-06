/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.observation;

/** Optional exporter hook. Applications can bridge it to Micrometer or OpenTelemetry. */
@FunctionalInterface
public interface VectorStoreObservationHook {

    void onEvent(VectorStoreObservationEvent event);

    static void safeEmit(VectorStoreObservationHook hook, VectorStoreObservationEvent event) {
        if (hook == null) {
            return;
        }
        try {
            hook.onEvent(event);
        } catch (RuntimeException ignored) {
            // Observability must never alter vector operation semantics.
        }
    }
}
