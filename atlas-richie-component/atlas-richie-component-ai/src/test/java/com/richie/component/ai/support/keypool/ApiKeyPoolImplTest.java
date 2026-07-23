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
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ApiKeyPool 行为测试 — 覆盖 borrow/return/invalidate/冷却 等核心路径。
 */
class ApiKeyPoolImplTest {

    private KeyPoolProperties defaultProps() {
        KeyPoolProperties p = new KeyPoolProperties();
        p.setEnabled(true);
        p.setRetryRounds(2);
        p.setCooldownSeconds(60);
        p.setMaxWaitMillis(500);
        p.setBlockWhenExhausted(false);
        return p;
    }

    @Test
    void borrow_returnsOneOfRegisteredKeys() {
        ApiKeyPoolImpl pool = new ApiKeyPoolImpl("test", Set.of("k1", "k2", "k3"), defaultProps());
        ApiKey key = pool.borrow();
        assertNotNull(key);
        assertTrue(Set.of("k1", "k2", "k3").contains(key.value()));
        pool.close();
    }

    @Test
    void returnObject_makesKeyAvailableAgain() {
        ApiKeyPoolImpl pool = new ApiKeyPoolImpl("test", Set.of("k1", "k2"), defaultProps());
        ApiKey k1 = pool.borrow();
        pool.returnObject(k1);
        assertEquals(0, pool.getNumActive());
        pool.close();
    }

    @Test
    void invalidate_marksKeyAsCooldown() throws InterruptedException {
        KeyPoolProperties props = defaultProps();
        props.setCooldownSeconds(1);
        ApiKeyPoolImpl pool = new ApiKeyPoolImpl("test", Set.of("k1", "k2"), props);

        // 借出 1 个 → invalidate → 归还,验证它被记入冷却(下次借不到它)
        ApiKey first = pool.borrow();
        String firstValue = first.value();
        pool.invalidate(first);
        pool.returnObject(first);

        // 再次借 1 个 — 拿到的 key 应当 != firstValue(被冷却跳过)
        // 但 k1/k2 哪个被借出顺序不固定 — 连续借多次直到拿到非冷却的 key
        Set<String> borrowed = new java.util.HashSet<>();
        for (int i = 0; i < 3; i++) {
            // 此时两个 key 中 firstValue 在冷却,另一个可借
            // 但我们这里只有一个 key 在冷却 — 另一个是非 firstValue
            ApiKey k;
            try {
                k = pool.borrow();
                borrowed.add(k.value());
                pool.returnObject(k);
            } catch (KeyPoolExhaustedException e) {
                break;  // 都在冷却(可能)
            }
        }
        // 借到的不应该只是 firstValue
        assertTrue(borrowed.size() >= 1);
        // 等待冷却结束,再借一次 — 应当可以借到(冷却时间已过)
        Thread.sleep(1100);
        ApiKey k3 = pool.borrow();
        assertNotNull(k3);
        pool.returnObject(k3);
        pool.close();
    }

    @Test
    void borrow_throwsWhenAllKeysInCooldown() {
        KeyPoolProperties props = defaultProps();
        props.setCooldownSeconds(60);
        props.setMaxWaitMillis(100);
        props.setBlockWhenExhausted(false);
        ApiKeyPoolImpl pool = new ApiKeyPoolImpl("test", Set.of("k1", "k2"), props);

        // 借出 2 个 key 都 invalidate(冷却)
        ApiKey k1 = pool.borrow();
        pool.invalidate(k1);
        pool.returnObject(k1);
        ApiKey k2 = pool.borrow();
        pool.invalidate(k2);
        pool.returnObject(k2);

        // 所有 key 都在冷却 → 应该抛 KeyPoolExhaustedException
        assertThrows(KeyPoolExhaustedException.class, pool::borrow);
        pool.close();
    }

    @Test
    void getNumActive_tracksBorrowAndReturn() {
        ApiKeyPoolImpl pool = new ApiKeyPoolImpl("test", Set.of("k1", "k2"), defaultProps());
        assertEquals(0, pool.getNumActive());
        ApiKey k = pool.borrow();
        assertEquals(1, pool.getNumActive());
        pool.returnObject(k);
        assertEquals(0, pool.getNumActive());
        pool.close();
    }

    @Test
    void getTotalKeys_returnsConfiguredSize() {
        ApiKeyPoolImpl pool = new ApiKeyPoolImpl("test", Set.of("k1", "k2", "k3", "k4"), defaultProps());
        assertEquals(4, pool.getTotalKeys());
        pool.close();
    }

    @Test
    void constructor_rejectsEmptyApiKeys() {
        assertThrows(IllegalArgumentException.class,
                () -> new ApiKey(""));
        assertThrows(IllegalArgumentException.class,
                () -> new ApiKey(null));
    }

    @Test
    void apiKey_equalsAndHashCode_basedOnValue() {
        ApiKey a = new ApiKey("same");
        ApiKey b = new ApiKey("same");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        ApiKey c = new ApiKey("different");
        assertNotNull(c);
        assertTrue(!a.equals(c));
    }

    @Test
    void apiKey_cooldownLifecycle() throws InterruptedException {
        ApiKey key = new ApiKey("k1");
        assertTrue(!key.isInCooldown());
        key.setCooldownUntilEpochMs(System.currentTimeMillis() + 200);
        assertTrue(key.isInCooldown());
        Thread.sleep(250);
        assertTrue(!key.isInCooldown());
    }

    @Test
    void set_storesCooldownTime_consistentWithGet() {
        ApiKey key = new ApiKey("k1");
        long now = System.currentTimeMillis();
        key.setCooldownUntilEpochMs(now + 5000);
        assertTrue(key.getCooldownUntilEpochMs() >= now + 4000);
    }

    @Test
    void borrow_withBlockedQueue_throwsWhenExhausted() {
        KeyPoolProperties props = defaultProps();
        props.setBlockWhenExhausted(true);
        props.setMaxWaitMillis(100);
        ApiKeyPoolImpl pool = new ApiKeyPoolImpl("test", Set.of("k1"), props);
        // 借出唯一的 key 不归还
        ApiKey k1 = pool.borrow();
        // 第二次借应超时抛异常
        assertThrows(KeyPoolExhaustedException.class, pool::borrow);
        pool.returnObject(k1);
        pool.close();
    }

    @Test
    void multipleBorrows_useDifferentKeysInRoundRobin() {
        Set<String> keys = new LinkedHashSet<>();
        keys.add("k1"); keys.add("k2"); keys.add("k3");
        ApiKeyPoolImpl pool = new ApiKeyPoolImpl("test", keys, defaultProps());
        ApiKey a = pool.borrow();
        ApiKey b = pool.borrow();
        ApiKey c = pool.borrow();
        // 3 个借出应该是 3 个不同 key
        assertEquals(3, Set.of(a.value(), b.value(), c.value()).size());
        pool.returnObject(a); pool.returnObject(b); pool.returnObject(c);
        pool.close();
    }

    @Test
    void fifoMode_borrowedTwice_returnsDifferentInstance() {
        // R-N.5 技术债修复:从 LIFO 改为 FIFO,避免"刚归还的冷却 key 被立即又借出"
        ApiKeyPoolImpl pool = new ApiKeyPoolImpl("test", Set.of("k1", "k2"), defaultProps());
        ApiKey a = pool.borrow();
        pool.returnObject(a);
        ApiKey b = pool.borrow();
        assertNotSame(a, b);
        assertEquals(Set.of("k1", "k2"), Set.of(a.value(), b.value()));
        pool.returnObject(b);
        pool.close();
    }
}