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

import com.richie.component.ai.config.keypool.KeyPoolProperties;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 默认 {@link ApiKeyPool} 实现 — 基于 Apache Commons Pool2 {@code GenericObjectPool}。
 *
 * <h2>关键设计:FIFO + 冷却 Set + 跳过冷却 key</h2>
 *
 * <p>为何用 FIFO 而非 LIFO:
 * <ul>
 *   <li>LIFO 模式下 {@code pool.borrowObject} 总是返回最近 {@code returnObject} 的那个 key。
 *       如果该 key 刚被 invalidate 进入冷却,下次 borrow 还会拿到它 — 形成"借到 → 冷却 → 归还 → 再借到"的死循环,
 *       需要靠 {@code attempts} 上限硬性跳出。</li>
 *   <li>FIFO 模式下 {@code returnObject} 把 key 放到队尾,下次 borrow 从队头取 — 自然轮转。
 *       配合 {@link #cooldownKeys} Set 做 O(1) 跳过,可避免上述循环。</li>
 * </ul>
 *
 * <p>关键不变量:
 * <ol>
 *   <li>{@link #invalidate(ApiKey)} 设置 cooldown 时间戳 + 加入 {@code cooldownKeys} Set</li>
 *   <li>{@link #borrow()} 借出后检查 {@code cooldownKeys.contains(key) || key.isInCooldown()}:
 *       冷却中 → 立即 {@code returnObject} 继续借下一个</li>
 *   <li>FIFO 保证不会立即又借到同一个冷却 key</li>
 *   <li>冷却结束 → {@code cooldownKeys} 仍包含 key(用于 {@code getNumCooldown} 统计),
 *       但 {@code isInCooldown()} 返回 false → 借出时不再被跳过</li>
 * </ol>
 *
 * @author richie696
 */
public class ApiKeyPoolImpl implements ApiKeyPool {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyPoolImpl.class);

    /** "全部 key 都被借出" 的等待上限,防止 borrowObject 永久阻塞。 */
    private static final Duration BORROW_WAIT = Duration.ofSeconds(2);

    private final String poolName;
    private final KeyPoolProperties properties;
    private final GenericObjectPool<ApiKey> pool;

    /**
     * 处于冷却中的 key 集合 — 借出时跳过这些 key。
     * <p>
     * 双重保险:
     * <ol>
     *   <li>FIFO + 此 Set → 借出时直接 O(1) 跳过冷却 key,无需依赖时间戳比较</li>
     *   <li>{@link #getNumCooldown()} 用 Set 精确统计,而不是从 {@code total - active - idle} 估算</li>
     * </ol>
     * <p>key 冷却结束后仍保留在 Set 中(用于统计),{@link #borrow()} 通过
     * {@link ApiKey#isInCooldown()} 时间戳检查自动放行。
     */
    private final Set<ApiKey> cooldownKeys = ConcurrentHashMap.newKeySet();

    public ApiKeyPoolImpl(String poolName, Set<String> apiKeys, KeyPoolProperties properties) {
        this.poolName = poolName;
        this.properties = properties;
        this.pool = new GenericObjectPool<>(new ApiKeyPooledFactory(apiKeys), buildConfig(apiKeys));
        // 预热:把全部 key 放入池(借出/归还)
        for (String k : apiKeys) {
            try {
                this.pool.addObjects(1);
            } catch (Exception e) {
                throw new IllegalStateException("ApiKeyPool[" + poolName + "] 预热失败", e);
            }
        }
        log.info("ApiKeyPool[{}] 初始化: totalKeys={}, enabled={}, retryRounds={}, cooldownSeconds={}, maxWaitMillis={}, blockWhenExhausted={}",
                poolName, apiKeys.size(), properties.isEnabled(), properties.getRetryRounds(),
                properties.getCooldownSeconds(), properties.getMaxWaitMillis(),
                properties.isBlockWhenExhausted(), true);
    }

    private GenericObjectPoolConfig<ApiKey> buildConfig(Set<String> apiKeys) {
        GenericObjectPoolConfig<ApiKey> cfg = new GenericObjectPoolConfig<>();
        cfg.setMaxTotal(apiKeys.size());
        cfg.setMaxIdle(apiKeys.size());
        cfg.setMinIdle(0);
        cfg.setTestOnBorrow(false);
        cfg.setTestOnReturn(false);
        cfg.setTestWhileIdle(false);
        cfg.setBlockWhenExhausted(properties.isBlockWhenExhausted());
        cfg.setMaxWait(BORROW_WAIT);
        cfg.setTimeBetweenEvictionRuns(Duration.ofSeconds(30));
        // 关键:FIFO 模式 — 配合 cooldownKeys Set 实现"借出时跳过冷却 key 且不重复借到"
        cfg.setLifo(false);
        return cfg;
    }

    @Override
    public ApiKey borrow() {
        long startMs = System.currentTimeMillis();
        long maxWaitMs = properties.getMaxWaitMillis();
        int attempts = 0;
        while (true) {
            attempts++;
            if (attempts > properties.getRetryRounds() * pool.getMaxTotal() + 1) {
                // 已超过 "retryRounds 轮 * 总 key 数" 次尝试,触发耗尽
                throw exhausted(startMs, null);
            }
            ApiKey key;
            try {
                key = pool.borrowObject(BORROW_WAIT);
            } catch (NoSuchElementException e) {
                // 池空 — 全部 key 都被借出 + blockWhenExhausted=true 已超时
                throw exhausted(startMs, e);
            } catch (Exception e) {
                throw new KeyPoolExhaustedException("ApiKeyPool[" + poolName + "] borrowObject 失败", e);
            }
            // 双重冷却检查:① cooldownKeys Set ② ApiKey.isInCooldown() 时间戳
            if (cooldownKeys.contains(key) || key.isInCooldown()) {
                // 借到一个冷却中的 key — 立即归还,继续借下一个
                // FIFO 保证下次借到的是不同 key
                pool.returnObject(key);
                if (System.currentTimeMillis() - startMs > maxWaitMs) {
                    throw exhausted(startMs, null);
                }
                continue;
            }
            log.debug("ApiKeyPool[{}] borrow 成功, active={}", poolName, pool.getNumActive());
            return key;
        }
    }

    @Override
    public void returnObject(ApiKey key) {
        if (key == null) return;
        try {
            pool.returnObject(key);
        } catch (Exception e) {
            log.warn("ApiKeyPool[{}] returnObject 失败, 强制 invalidate", poolName, e);
            try {
                pool.invalidateObject(key);
            } catch (Exception ex) {
                log.warn("ApiKeyPool[{}] invalidateObject 也失败", poolName, ex);
            }
        }
    }

    @Override
    public void invalidate(ApiKey key) {
        if (key == null) return;
        long cooldownMs = properties.getCooldownSeconds() * 1000L;
        key.setCooldownUntilEpochMs(System.currentTimeMillis() + cooldownMs);
        cooldownKeys.add(key);
        try {
            pool.returnObject(key);
        } catch (Exception e) {
            log.warn("ApiKeyPool[{}] invalidate 后的 returnObject 失败", poolName, e);
        }
        log.warn("ApiKeyPool[{}] key 失效, 进入冷却: cooldownSeconds={}", poolName, properties.getCooldownSeconds());
    }

    @Override
    public int getNumActive() {
        return pool.getNumActive();
    }

    @Override
    public int getTotalKeys() {
        return pool.getMaxTotal();
    }

    @Override
    public int getNumCooldown() {
        // 清理过期的 Set 成员(时间戳已过期但 Set 仍持有的 key)
        cooldownKeys.removeIf(k -> !k.isInCooldown());
        return cooldownKeys.size();
    }

    private KeyPoolExhaustedException exhausted(long startMs, Throwable cause) {
        long elapsed = System.currentTimeMillis() - startMs;
        return new KeyPoolExhaustedException(
                String.format("ApiKeyPool[%s] 在 %dms 内无可用 key (totalKeys=%d, active=%d, cooldown=%d)",
                        poolName, elapsed, pool.getMaxTotal(), pool.getNumActive(), getNumCooldown()),
                cause,
                properties.getRetryRounds(),
                pool.getMaxTotal(),
                getNumCooldown());
    }

    public void close() {
        pool.close();
    }

    /**
     * Commons Pool2 工厂 — 负责按序创建不同的 ApiKey 实例。
     * <p>每次 {@code create()} 返回池中"下一个未创建"的 key,保证所有 key 都是不同的 ApiKey 实例。
     */
    private static class ApiKeyPooledFactory extends BasePooledObjectFactory<ApiKey> {
        private final java.util.List<ApiKey> allKeys;
        private final java.util.concurrent.atomic.AtomicInteger createIndex = new java.util.concurrent.atomic.AtomicInteger(0);

        ApiKeyPooledFactory(Set<String> apiKeys) {
            this.allKeys = new java.util.ArrayList<>(apiKeys.size());
            int idx = 0;
            for (String k : apiKeys) {
                this.allKeys.add(new ApiKey(k, idx++));
            }
        }

        @Override
        public ApiKey create() {
            int idx = createIndex.getAndIncrement();
            if (idx < allKeys.size()) {
                return allKeys.get(idx);
            }
            // 溢出 — 池被配置成 maxTotal > apiKeys.size() 时不会发生;
            // 若发生,降级:返回任一非冷却 key。
            for (ApiKey k : allKeys) {
                if (!k.isInCooldown()) {
                    return k;
                }
            }
            return allKeys.get(0);
        }

        @Override
        public PooledObject<ApiKey> wrap(ApiKey obj) {
            return new DefaultPooledObject<>(obj);
        }
    }
}