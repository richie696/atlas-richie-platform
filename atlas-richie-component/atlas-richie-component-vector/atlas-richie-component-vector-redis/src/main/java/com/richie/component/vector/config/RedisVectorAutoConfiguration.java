/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.vector.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import redis.clients.jedis.RedisClient;

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
        RedisClient jedisClient = RedisClient.builder().hostAndPort(host, port).build();
        String indexName = vectorProperties.getDefaultIndex();
        log.info("初始化Redis向量存储，索引名: {}", indexName);
        return RedisVectorStore.builder(jedisClient, embeddingModel)
                .indexName(indexName)
                .initializeSchema(true)
                .build();
    }

}
