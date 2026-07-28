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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Qdrant 向量数据库的连接与集合配置。
 *
 * <p>该类型作为 Qdrant provider 模块的 {@code @ConfigurationProperties} 载体，绑定
 * {@code platform.component.vector.qdrant.*} 命名空间下的配置项，向
 * {@link QdrantVectorAutoConfiguration} 注入运行时需要的连接目标、TLS 选项和默认
 * collection 名称。它只承担“Qdrant 原生层”的连接配置职责；通用的索引维度、
 * 距离度量、批量流水线等由 {@code core} 模块的 {@code VectorProperties} 统一表达，
 * 二者通过 {@link cn.richie696.component.vector.service.VectorService} 的实现类
 * 在 Spring 容器内组合。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "platform.component.vector.qdrant")
public class QdRantConfig {

    /**
     * Qdrant gRPC 服务地址（不含端口）。
     */
    private String host;

    /**
     * Qdrant gRPC 端口，默认 6333。
     */
    private Integer port = 6333;

    /**
     * 是否启用 TLS（gRPC TLS）。生产环境推荐为 {@code true}。
     */
    private boolean useTransportLayerSecurity = false;

    /**
     * 默认 collection 名称，业务侧未显式指定时使用该值。
     */
    private String collection = "documents";

    /**
     * 启动时是否由 Spring AI 自动创建/校验 collection schema。生产环境建议关闭，
     * 由运维显式管理 schema。
     */
    private boolean initializeSchema = false;
}
