/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.contract.model;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 租户功能全局开关
 *
 * <p>该标志位由 {@code atlas-richie-component-tenant} 在启动时自动设置为 {@code true}，
 * 实现租户模块的热插拔：只要该组件在 classpath 上且 Spring 上下文已刷新，此标志即为 {@code true}。</p>
 *
 * <p>JwtUtils 在生成令牌时读取此标志决定是否向 JWT claims 中写入 {@code tenantEnabled=true}，
 * {@code TenantFilter} 再依据 JWT 中的 {@code tenantEnabled} 决定是否执行租户校验。</p>
 *
 * @author richie696
 * @since 1.0
 */
public final class TenantFeature {

    private static final AtomicBoolean ENABLED = new AtomicBoolean(false);

    /**
     * 判断租户功能是否已启用
     *
     * @return 组件已加载且启用返回 true
     */
    public static boolean isEnabled() {
        return ENABLED.get();
    }

    /**
     * 设置租户功能启用状态（由自动配置调用）
     *
     * @param enabled 是否启用
     */
    public static void setEnabled(boolean enabled) {
        ENABLED.set(enabled);
    }

    private TenantFeature() {
    }
}
