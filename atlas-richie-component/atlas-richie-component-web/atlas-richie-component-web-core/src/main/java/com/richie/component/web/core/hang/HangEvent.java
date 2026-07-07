package com.richie.component.web.core.hang;

import com.richie.component.web.core.hook.HookEvent;

import java.util.Arrays;

/**
 * Hang 事件（README.md §4.4）。
 * <p>
 * 请求耗时超过 {@link #thresholdMillis} 时由 {@link HangDetectionInterceptor} publish 到 {@link com.richie.component.web.core.hook.HookBus}。
 *
 * @author richie696
 * @since 2026-07
 */
public record HangEvent(
        String method,
        String path,
        long elapsedMillis,
        long thresholdMillis,
        String clientKey,
        String traceId,
        StackTraceElement[] stackTrace
) implements HookEvent {

    private static final int MAX_STACK_FRAMES = 50;

    public static HangEvent of(String method, String path, long elapsedMillis,
                               long thresholdMillis, String clientKey, String traceId) {
        return new HangEvent(method, path, elapsedMillis, thresholdMillis,
                clientKey, traceId, stackOf(Thread.currentThread()));
    }

    public static StackTraceElement[] stackOf(Thread thread) {
        StackTraceElement[] full = thread.getStackTrace();
        if (full.length <= MAX_STACK_FRAMES) {
            return full;
        }
        return Arrays.copyOf(full, MAX_STACK_FRAMES);
    }
}