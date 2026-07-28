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
package cn.richie696.component.vector.config;

import cn.richie696.component.vector.service.impl.RedisVectorServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import redis.clients.jedis.RedisClient;

/**
 * Redis 向量存储的 Spring Boot 自动配置入口。
 *
 * <p>当 {@code platform.component.vector.provider=redis} 时激活，负责把 Redis Stack
 * 的向量检索能力以 Spring AI {@link VectorStore} Bean 的形式注册到容器，并通过
 * {@link Import} 同时加载 {@link RedisVectorServiceImpl}，使上层可以直接面向统一的
 * {@link cn.richie696.component.vector.service.VectorService} 契约编程，而无需感知底层
 * Jedis 连接与 RediSearch 模块的存在。</p>
 *
 * <p>本类只负责连接装配与索引命名透传（默认索引名取自
 * {@link VectorProperties#getDefaultIndex()}），不感知业务字段结构；向量字段 schema
 * 的首次初始化由 Spring AI 的 {@link RedisVectorStore.Builder#initializeSchema(boolean)}
 * 触发，运维侧只需保证 Redis 已部署 RediSearch 模块（Redis Stack）。</p>
 */
@Slf4j
@AutoConfiguration
@Import(RedisVectorServiceImpl.class)
public class RedisVectorAutoConfiguration {

    /**
     * 构造可直接用于 add/search 的 Redis Stack 向量存储。
     *
     * <p>前置条件：{@code redisConnectionFactory} 必须是
     * {@link JedisConnectionFactory}。RediSearch 模块只通过 Jedis 暴露底层搜索
     * 能力，Lettuce 等其他实现无法完成 {@code FT.SEARCH}/{@code FT.CREATE} 等命令；
     * 若检测到非 Jedis 工厂，本方法会抛出 {@link IllegalStateException}，由运维通过
     * {@code spring.data.redis.client-type=jedis} 或调整 {@code richie-component-cache}
     * 的 Lettuce 定制来修正。</p>
     *
     * <p>副作用：调用 {@link JedisConnectionFactory#afterPropertiesSet()} 触发
     * Jedis 客户端初始化，并以 {@code initializeSchema(true)} 让 Redis 在首次写入前
     * 自动按 embedding 维度创建向量索引。索引名复用
     * {@link VectorProperties#getDefaultIndex()}，与后续所有向量检索共享同一索引。</p>
     *
     * @param redisConnectionFactory Spring Boot 自动注入的 Redis 连接工厂，必须是 Jedis 实现
     * @param embeddingModel AI 组件提供的 EmbeddingModel，用于把文本转换为向量
     * @param vectorProperties 通用向量配置，仅读取其默认索引名
     * @return 已构建完成的 Redis 向量存储
     * @throws IllegalStateException 当前连接工厂不是 Jedis 实现时抛出，提示运维切换 client-type
     */
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
