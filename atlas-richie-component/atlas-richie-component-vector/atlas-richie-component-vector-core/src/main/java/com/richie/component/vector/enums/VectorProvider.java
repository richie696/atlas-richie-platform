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
package com.richie.component.vector.enums;

/**
 * 向量数据库提供商枚举
 * 定义支持的向量数据库类型
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public enum VectorProvider {

    /**
     * Redis向量数据库
     * 基于Redis Stack的向量搜索功能
     * <p>
     * 特点：
     * - 内存存储，检索速度快
     * - 配置简单，学习成本低
     * - 成熟稳定，运维经验丰富
     * - 成本低，适合中小规模应用
     * <p>
     * 适用场景：
     * - 中小规模向量检索（百万级以下）
     * - 对延迟要求极高的场景
     * - 已有Redis基础设施的项目
     * - 快速原型开发
     */
    REDIS,

    /**
     * Milvus向量数据库
     */
    MILVUS,

    /**
     * MongoDB向量数据库
     * 基于MongoDB 7.0+的向量搜索功能
     * <p>
     * 特点：
     * - 文档友好，天然支持复杂文档结构
     * - 查询灵活，支持向量+标量混合查询
     * - 扩展性好，分片集群支持大规模数据
     * - 生态完善，MongoDB生态丰富
     * <p>
     * 适用场景：
     * - 需要向量+文档混合查询
     * - 大规模数据存储和检索
     * - 复杂业务逻辑场景
     * - 已有MongoDB基础设施
     */
    MONGODB,

    /**
     * PostgreSQL向量数据库
     * 基于pgvector扩展的向量搜索功能
     * <p>
     * 特点：
     * - 关系型数据库，ACID事务支持
     * - 成熟的SQL查询能力
     * - 支持复杂的向量+关系查询
     * - 数据一致性好
     * <p>
     * 适用场景：
     * - 需要事务支持的场景
     * - 复杂的向量+关系查询
     * - 已有PostgreSQL基础设施
     * - 对数据一致性要求高的场景
     */
    POSTGRESQL,

    /**
     * Qdrant向量数据库
     * 高性能的向量数据库
     * <p>
     * 特点：
     * - 高性能，Rust开发
     * - 支持多种距离度量
     * - 丰富的过滤和查询功能
     * - 支持实时更新
     * <p>
     * 适用场景：
     * - 高性能要求
     * - 大规模向量检索
     * - 实时更新需求
     * - 生产环境部署
     */
    QDRANT,

    /**
     * Neo4j向量数据库
     * 基于Neo4j的图数据库与向量检索能力
     * <p>
     * 特点：
     * - 图数据库，天然支持关系型和图结构数据
     * - 支持向量检索与图算法结合
     * - 适合知识图谱、社交网络等复杂关系场景
     * - 可扩展性强，支持大规模图数据
     * <p>
     * 适用场景：
     * - 需要图结构与向量检索结合的场景
     * - 知识图谱、社交网络、推荐系统
     * - 复杂实体关系分析
     * - 已有Neo4j基础设施
     */
    NEO4J,

    /**
     * Elasticsearch向量数据库
     * 基于Elasticsearch的向量检索能力
     * <p>
     * 特点：
     * - 支持全文检索与向量检索混合查询
     * - 分布式架构，易于扩展
     * - 生态丰富，集成方便
     * - 支持多种距离度量与过滤
     * <p>
     * 适用场景：
     * - 需要全文+向量混合检索
     * - 大规模数据分布式检索
     * - 日志、文档、图片等多模态检索
     * - 已有Elasticsearch基础设施
     */
    ELASTICSEARCH,

    /**
     * Weaviate向量数据库
     */
    WEAVIATE;

}
