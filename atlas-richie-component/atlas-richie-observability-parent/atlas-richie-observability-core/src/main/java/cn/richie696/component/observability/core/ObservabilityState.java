/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.observability.core;

import java.util.Objects;

/**
 * 统一可观测性有效状态。
 *
 * <p>该对象是不可变的运行时契约，不负责读取 Spring 配置。Spring 自动配置负责把
 * atlas.observability.enabled 和 otel.sdk.disabled 解析成该状态，Core 和各适配器只依赖
 * 这个结果。</p>
 */
public final class ObservabilityState {

    private final boolean configuredEnabled;
    private final boolean sdkDisabled;

    public ObservabilityState(boolean configuredEnabled, boolean sdkDisabled) {
        this.configuredEnabled = configuredEnabled;
        this.sdkDisabled = sdkDisabled;
    }

    public static ObservabilityState enabledState() {
        return new ObservabilityState(true, false);
    }

    public static ObservabilityState disabledState() {
        return new ObservabilityState(false, true);
    }

    public boolean configuredEnabled() {
        return configuredEnabled;
    }

    public boolean sdkDisabled() {
        return sdkDisabled;
    }

    public boolean enabled() {
        return configuredEnabled && !sdkDisabled;
    }

    public boolean disabled() {
        return !enabled();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ObservabilityState that)) {
            return false;
        }
        return configuredEnabled == that.configuredEnabled && sdkDisabled == that.sdkDisabled;
    }

    @Override
    public int hashCode() {
        return Objects.hash(configuredEnabled, sdkDisabled);
    }

    @Override
    public String toString() {
        return "ObservabilityState{"
                + "configuredEnabled=" + configuredEnabled
                + ", sdkDisabled=" + sdkDisabled
                + ", enabled=" + enabled()
                + '}';
    }
}
