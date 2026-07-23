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
 * 池中流通的 API Key 值对象。
 *
 * <p>设计为不可变 — 创建后 value / cooldownEpoch 秒数冻结,只有池实现能更新 cooldown。
 *
 * @author richie696
 */
public final class ApiKey {

    private final String value;
    private final int createIndex;
    private volatile long cooldownUntilEpochMs;

    public ApiKey(String value) {
        this(value, -1);
    }

    public ApiKey(String value, int createIndex) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ApiKey value 不能为空");
        }
        this.value = value;
        this.createIndex = createIndex;
        this.cooldownUntilEpochMs = 0L;
    }

    public String value() {
        return value;
    }

    /**
     * 池中唯一索引 — 用于在外部 Map 中定位对应的 per-key ChatModel / RerankModel 等实例。
     * <p>
     * 由 {@code ApiKeyPooledFactory.create()} 在创建时分配(0..N-1),
     * 业务装饰器通过 {@code perKeyModels.get(key.createIndex())} 拿到对应实例。
     * <p>
     * 当 -1 时表示"未在池中创建"(由 {@code new ApiKey(value)} 直接构造的场景)。
     */
    public int getCreateIndex() {
        return createIndex;
    }

    public long getCooldownUntilEpochMs() {
        return cooldownUntilEpochMs;
    }

    public void setCooldownUntilEpochMs(long ts) {
        this.cooldownUntilEpochMs = ts;
    }

    public boolean isInCooldown() {
        return System.currentTimeMillis() < cooldownUntilEpochMs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApiKey)) return false;
        return value.equals(((ApiKey) o).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "ApiKey{***cooldown=" + (cooldownUntilEpochMs > 0 ? "yes" : "no") + "}";
    }
}