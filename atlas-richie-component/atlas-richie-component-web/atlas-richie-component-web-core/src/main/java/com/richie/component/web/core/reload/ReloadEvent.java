package com.richie.component.web.core.reload;

import com.richie.component.web.core.hook.HookEvent;

import java.time.Instant;

/**
 * 配置重载事件（README.md §4.6）。
 * <p>
 * 业务拦截器在 HotReloadRegistry 触发 reload 时发布此事件，
 * 供审计 / 通知 / metrics 订阅者使用。
 *
 * @param name      Reloadable 注册名
 * @param timestamp 重载触发时刻
 * @author richie696
 * @since 2026-07
 */
public record ReloadEvent(
        String name,
        Instant timestamp
) implements HookEvent {

    public static ReloadEvent of(String name) {
        return new ReloadEvent(name, Instant.now());
    }
}