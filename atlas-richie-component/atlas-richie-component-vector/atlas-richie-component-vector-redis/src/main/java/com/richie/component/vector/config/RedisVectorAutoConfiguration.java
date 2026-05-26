package com.richie.component.vector.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import redis.clients.jedis.JedisPooled;

@Slf4j
@Configuration
public class RedisVectorAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "platform.component.vector", name = "provider", havingValue = "redis")
    public VectorStore redisVectorStore(RedisConnectionFactory redisConnectionFactory,
                                        EmbeddingModel embeddingModel,
                                        VectorProperties vectorProperties) {
        if (!(redisConnectionFactory instanceof JedisConnectionFactory jedisConnFactory)) {
            throw new IllegalStateException(
                    "Redis向量搜索需要Jedis连接（Redis Stack要求），当前连接类型为: "
                            + redisConnectionFactory.getClass().getName()
                            + "。请确保未启用richie-component-cache的Lettuce定制，或显式配置spring.data.redis.client-type=jedis");
        }
        jedisConnFactory.afterPropertiesSet();
        String host = jedisConnFactory.getHostName();
        int port = jedisConnFactory.getPort();
        JedisPooled jedisPooled;
        GenericObjectPoolConfig<redis.clients.jedis.Connection> poolConfig = jedisConnFactory.getPoolConfig();
        if (poolConfig != null) {
            jedisPooled = new JedisPooled(poolConfig, host, port);
        } else {
            jedisPooled = new JedisPooled(host, port);
        }
        String indexName = vectorProperties.getDefaultIndex();
        log.info("初始化Redis向量存储，索引名: {}", indexName);
        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName(indexName)
                .initializeSchema(true)
                .build();
    }

}
