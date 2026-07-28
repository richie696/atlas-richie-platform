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

import cn.richie696.component.vector.service.impl.MilvusVectorServiceImpl;
import cn.richie696.component.vector.filter.MilvusVectorFilterCompiler;
import cn.richie696.component.vector.filter.VectorFilterCompiler;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Milvus provider 的 Spring Boot 自动配置入口。
 *
 * <p>当 {@code platform.component.vector.provider=milvus} 时激活，向容器中注册一个
 * 名称为 {@code vectorStore} 的 Spring AI {@link VectorStore} Bean 以及一个共享的
 * {@link MilvusServiceClient} 连接，使 {@link MilvusVectorServiceImpl} 可以无侵入地
 * 走与其它 provider 完全一致的 {@link cn.richie696.component.vector.service.VectorService}
 * 抽象层。Connection / index / SSL / 认证等 Milvus 私有参数全部收口在 {@link MilvusConfig}，
 * 与 {@link cn.richie696.component.vector.config.VectorProperties} 的通用索引意图对齐。</p>
 */
@Slf4j
@AutoConfiguration
@AutoConfigureBefore(VectorAutoConfiguration.class)
@EnableConfigurationProperties({VectorProperties.class, MilvusConfig.class})
@Import(MilvusVectorServiceImpl.class)
public class MilvusVectorAutoConfiguration {

    /**
     * 构建 Spring AI Milvus {@link VectorStore}，作为 core 模块与 Milvus SDK 的桥接点。
     *
     * <p>维度 / 索引类型 / 度量类型等来自 {@link MilvusConfig} 而非
     * {@link VectorProperties.IndexConfig}，原因是这些值在 Milvus 中天然按
     * 大写枚举（{@link io.milvus.param.IndexType} / {@link io.milvus.param.MetricType}）传入，差异化字段被有意
     * 推到 provider 侧。schema 由 {@code initializeSchema(true)} 启动时自动建表。</p>
     *
     * @param milvusClient   已建连的 Milvus gRPC 客户端
     * @param embeddingModel 文本嵌入模型，用于 add/search 时的向量化
     * @param config         Milvus 配置（database / collection / index / metric）
     * @return Spring AI 统一的 {@link VectorStore} 视图
     */
    @Bean
    @ConditionalOnProperty(prefix = "platform.component.vector", name="provider", havingValue = "milvus")
    public VectorStore vectorStore(MilvusServiceClient milvusClient, EmbeddingModel embeddingModel, MilvusConfig config) {
        return MilvusVectorStore.builder(milvusClient, embeddingModel)
                .databaseName(config.getDatabaseName())
                .collectionName(config.getCollectionName())
                .indexType(config.getIndexType())
                .metricType(config.getMetricType())
                .batchingStrategy(new TokenCountBatchingStrategy())
                // 原生 MilvusServiceImpl 负责统一 schema / scalar ACL 字段；避免 Spring AI 以另一套字段定义建表。
                .initializeSchema(false)
                .build();
    }

    /** Milvus 原生 expression 编译器，供 ACL/知识库门面将结构化过滤安全地下推。 */
    @Bean
    @ConditionalOnProperty(prefix = "platform.component.vector", name = "provider", havingValue = "milvus")
    @ConditionalOnMissingBean(VectorFilterCompiler.class)
    public VectorFilterCompiler milvusVectorFilterCompiler() {
        return new MilvusVectorFilterCompiler();
    }

    /**
     * 构建并持有整个 provider 共享的 {@link MilvusServiceClient}。
     *
     * <p>该客户端是 Milvus SDK 的 V1 入口，所有后续 vectorStore /
     * {@link MilvusVectorServiceImpl} 中的 SDK 调用都走此实例，因此连接参数（host / port /
     * timeout / keep-alive / 认证 / SSL）都在这一步集中装配，避免在每个调用点重复构造。
     * 显式使用 {@code withHost} 而非 {@code withUri}，因为 ConnectParam#verify() 对
     * {@code withUri} 要求带 scheme 的完整 URL，而 {@code host} 字段只是裸主机名。</p>
     *
     * @param config Milvus 连接 / 认证 / SSL 配置
     * @return 可复用的 Milvus gRPC 客户端单例
     */
    @Bean
    @ConditionalOnProperty(prefix = "platform.component.vector", name = "provider", havingValue = "milvus")
    public MilvusServiceClient milvusClient(MilvusConfig config) {
        // 用 withHost 而非 withUri：host 字段是裸主机名，ConnectParam.verify() 对 withUri 要求带 scheme 的完整 URL。
        ConnectParam.Builder builder = ConnectParam.newBuilder()
                .withHost(config.getHost())
                .withPort(config.getPort())
                .withConnectTimeout(config.getConnectTimeoutMs(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .withKeepAliveTime(config.getKeepAliveTimeMs(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .withKeepAliveTimeout(config.getKeepAliveTimeoutMs(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .withIdleTimeout(config.getIdleTimeoutMs(), java.util.concurrent.TimeUnit.MILLISECONDS);

        // 认证配置
        if (config.getUsername() != null && config.getPassword() != null) {
            builder.withAuthorization(config.getUsername(), config.getPassword());
        }

        // SSL配置
        if (config.isSecure()) {
            if (config.getServerPemPath() != null) {
                builder.withServerPemPath(config.getServerPemPath());
            }
            if (config.getServerName() != null) {
                builder.withServerName(config.getServerName());
            }
            if (config.getCaPemPath() != null) {
                builder.withCaPemPath(config.getCaPemPath());
            }
            if (config.getClientKeyPath() != null && config.getClientPemPath() != null) {
                builder.withClientKeyPath(config.getClientKeyPath())
                        .withClientPemPath(config.getClientPemPath());
            }
        }

        return new MilvusServiceClient(builder.build());
    }
}
