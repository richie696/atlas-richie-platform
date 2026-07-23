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
 * 池中 key 全部耗尽(被限流且 cooldown 中)时的异常 — 业务侧应降级或快速失败。
 *
 * <p>与 {@code NoSuchElementException}(borrow 超时)区分:本异常表示"key 都活着但都在冷却,
 * 短期内无解",通常需业务侧告警 + 触发配置中心动态扩容 key 池。
 *
 * @author richie696
 */
public class KeyPoolExhaustedException extends RuntimeException {

    private int retryRounds;
    private int totalKeys;
    private int numCooldown;

    public KeyPoolExhaustedException(String message) {
        super(message);
    }

    public KeyPoolExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }

    public KeyPoolExhaustedException(String message, Throwable cause,
                                     int retryRounds, int totalKeys, int numCooldown) {
        super(String.format("%s (retry-rounds=%d, totalKeys=%d, numCooldown=%d)",
                message, retryRounds, totalKeys, numCooldown), cause);
        this.retryRounds = retryRounds;
        this.totalKeys = totalKeys;
        this.numCooldown = numCooldown;
    }

    public KeyPoolExhaustedException(String businessName, int retryRounds,
                                     int totalKeys, int numCooldown, Throwable cause) {
        super(String.format("ApiKeyPool[%s] exhausted after %d retry rounds (totalKeys=%d, numCooldown=%d)",
                businessName, retryRounds, totalKeys, numCooldown), cause);
        this.retryRounds = retryRounds;
        this.totalKeys = totalKeys;
        this.numCooldown = numCooldown;
    }

    public int getRetryRounds() { return retryRounds; }
    public int getTotalKeys() { return totalKeys; }
    public int getNumCooldown() { return numCooldown; }
}