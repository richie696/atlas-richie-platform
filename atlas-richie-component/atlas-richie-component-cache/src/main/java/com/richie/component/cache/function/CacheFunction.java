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
