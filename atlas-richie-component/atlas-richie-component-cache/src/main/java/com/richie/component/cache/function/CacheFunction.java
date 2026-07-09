/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.cache.function;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 缓存函数
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-26 14:11:11
 */
public interface CacheFunction {

    /**
     * 锁的key
     */
    String LOCK_KEY = "LOCK_KEY_";
    /**
     * 锁的过期时间
     */
    long TIME_OUT = 3L;
    /**
     * 数据库加载锁的过期时间
     */
    long DB_LOADER_TIME_OUT = 10L;
    /**
     * 批量加载数据大小
     */
    int BATCH_SIZE = 20;


    /**
     * 生成1~10分钟的随机毫秒数（防止出现缓存雪崩）
     *
     * @return 随机毫秒数（1~10 分钟）
     */
    static long getRandomExtraMillis() {
        return ThreadLocalRandom.current().nextInt(1, 11) * 60_000L;
    }
}
