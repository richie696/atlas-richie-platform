/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.ai.support.keypool;

/**
 * API Key 池 — 业务门面,屏蔽 commons-pool2 细节。
 *
 * <h2>使用模式</h2>
 * <pre>{@code
 * ApiKey key = pool.borrow();
 * try {
 *     return doCall(key.value());
 * } catch (Exception e) {
 *     if (validator.isKeyInvalidating(e)) {
 *         pool.invalidate(key);  // 限流 → 池移除 + 进入冷却
 *     } else {
 *         pool.returnObject(key); // 正常归还
 *     }
 *     throw e;
 * }
 * }</pre>
 *
 * <h2>实现</h2>
 * 默认 {@link ApiKeyPoolImpl} 基于 Apache Commons Pool2 {@code GenericObjectPool},
 * 由 {@link ApiKeyPoolManager} 按业务名懒加载管理。
 *
 * @author richie696
 */
public interface ApiKeyPool {

    /**
     * 借一个 key。
     *
     * <p>行为:
     * <ul>
     *   <li>若池中所有 key 都处于冷却中 → 阻塞(由 {@code blockWhenExhausted} 配置)或立即抛</li>
     *   <li>借出时自动跳过冷却中的 key(即使已归还)</li>
     *   <li>借出时跳过已被 {@link #invalidate} 永久移除的 key</li>
     * </ul>
     *
     * @return 非 null 的 {@link ApiKey}
     * @throws KeyPoolExhaustedException 池空 + 超时 / 不可阻塞 / 所有 key 都在冷却
     */
    ApiKey borrow();

    /**
     * 正常归还 key — 后续可被其他调用方再次借出。
     * 若 key 处于冷却中,归还后冷却时间继续生效。
     */
    void returnObject(ApiKey key);

    /**
     * 标记 key 失效 — 池中立即移除,进入冷却期(由 {@code key-pool.cooldown-seconds} 控制)。
     *
     * <p>冷却期内 key 不可被借出;冷却结束后,池自动重新放行(由池 lazy 检测,不依赖定时任务)。
     */
    void invalidate(ApiKey key);

    /** 当前活跃借出数(健康检查用)。 */
    int getNumActive();

    /** 当前池大小(健康检查用)。 */
    int getTotalKeys();

    /** 当前处于冷却中的 key 数。 */
    int getNumCooldown();
}