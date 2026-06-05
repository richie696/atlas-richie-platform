package com.richie.testing.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Redis 集测基类：用例前清理 {@code it:*}（容器 flushDb / 外部 SCAN）。
 */
@Execution(ExecutionMode.SAME_THREAD)
public abstract class AbstractRedisIntegrationTestBase {

    private static final String IT_KEY_PATTERN = "it:*";

    @Autowired
    protected StringRedisTemplate stringRedisTemplate;

    protected abstract Supplier<RedisIntegrationTestAccess> redisIntegrationTestAccess();

    @BeforeEach
    void prepareRedisIntegrationTest() {
        RedisIntegrationTestAccess access = redisIntegrationTestAccess().get();
        if (access.isExternal()) {
            deleteByScan(IT_KEY_PATTERN);
        } else {
            var connection = stringRedisTemplate.getConnectionFactory().getConnection();
            try {
                connection.serverCommands().flushDb();
            } finally {
                connection.close();
            }
        }
        onRedisIntegrationTestPrepared();
    }

    /** 子类在 Redis 清理后追加装配（如静态门面委托）。 */
    protected void onRedisIntegrationTestPrepared() {
    }

    private void deleteByScan(String pattern) {
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(200).build();
        List<String> batch = new ArrayList<>();
        try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() >= 100) {
                    stringRedisTemplate.delete(batch);
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) {
            stringRedisTemplate.delete(batch);
        }
    }
}
