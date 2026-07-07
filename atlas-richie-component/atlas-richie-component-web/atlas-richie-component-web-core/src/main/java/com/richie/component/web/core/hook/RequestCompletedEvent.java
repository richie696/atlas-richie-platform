package com.richie.component.web.core.hook;

import com.richie.component.web.core.spi.WebRequestContext;

/**
 * 请求完成事件（README.md §4.5 HookBus）。
 * <p>
 * 由 {@link com.richie.component.web.core.servlet.InterceptingFilter} 在 finally 中
 * 派发，无论请求被短路 / 抛异常 / 正常完成。
 *
 * <h2>订阅者用法</h2>
 * <pre>
 *   hookBus.subscribe(RequestCompletedEvent.class, e -&gt; {
 *       // 累计耗时 / 写入 metrics / 上报业务事件
 *       long ms = (System.nanoTime() - e.startNanos()) / 1_000_000L;
 *       metrics.recordRequest(e.path(), e.responseStatus(), ms);
 *   });
 * </pre>
 *
 * @author richie696
 * @since 2026-07
 */
public record RequestCompletedEvent(
        String method,
        String path,
        int responseStatus,
        long startNanos,
        long endNanos,
        boolean shortCircuited,
        boolean hasError,
        String clientKey,
        String traceId
) implements HookEvent {

    public static RequestCompletedEvent of(WebRequestContext ctx, long endNanos) {
        return new RequestCompletedEvent(
                ctx.method(),
                ctx.path(),
                ctx.responseStatus(),
                ctx.startNanos(),
                endNanos,
                ctx.isShortCircuited(),
                ctx.error().isPresent(),
                ctx.clientKey(),
                ctx.traceId());
    }

    public long durationMillis() {
        return (endNanos - startNanos) / 1_000_000L;
    }
}