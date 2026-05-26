package com.richie.component.vector.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis Vector 配置冒烟测试
 *
 * <p>注意：完整集成测试需要运行 Redis 服务。
 * 当前测试仅验证配置绑定和 Bean 创建逻辑。
 */
class RedisVectorAutoConfigurationSmokeTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

    @Test
    void shouldNotCreateVectorBeansWhenProviderNotRedis() {
        contextRunner
                .withPropertyValues(
                        "platform.component.vector.provider=mongodb"
                )
                .run(context -> {
                    assertFalse(context.containsBean("redisVectorStore"),
                            "redisVectorStore should not be created when provider is not redis");
                    assertFalse(context.containsBean("redisVectorService"),
                            "redisVectorService should not be created when provider is not redis");
                });
    }
}