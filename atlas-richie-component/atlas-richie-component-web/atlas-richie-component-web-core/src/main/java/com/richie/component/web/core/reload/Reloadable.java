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
package com.richie.component.web.core.reload;

/**
 * 可热替换对象 SPI（README.md §4.6）。
 * <p>
 * 实现类（如 {@code RateLimitInterceptor} / {@code CircuitBreakerInterceptor} /
 * {@code PlatformProtectionInterceptor}）实现此接口，把可变配置封装为
 * {@code Reloadable<S>}。配置变更时调
 * {@link HotReloadRegistry#reload(String)} / {@code reloadAll()} 即可生效。
 *
 * <h2>典型实现</h2>
 * <pre>{@code
 *   public class RateLimitInterceptor implements Reloadable<RateLimitProperties> {
 *       private volatile RateLimitProperties current;
 *
 *       public RateLimitProperties currentState() { return current; }
 *       public void accept(RateLimitProperties newState) {
 *           this.current = newState;
 *       }
 *   }
 * }</pre>
 *
 * @param <T> 当前可变状态类型
 * @author richie696
 * @since 2026-07
 */
public interface Reloadable<T> {

    /**
     * 当前状态（用于 diff / 校验）。
     */
    T currentState();

    /**
     * 应用新状态。实现必须是幂等的（重复调用效果相同）+ 线程安全（其他线程可能同时
     * 正在读 currentState）。
     */
    void accept(T newState);

    /**
     * 注册名（用于日志 / 诊断）。
     */
    default String name() {
        return getClass().getSimpleName();
    }
}