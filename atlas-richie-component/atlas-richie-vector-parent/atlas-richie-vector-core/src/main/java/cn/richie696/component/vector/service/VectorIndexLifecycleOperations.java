package cn.richie696.component.vector.service;

import cn.richie696.component.vector.config.VectorProperties;

/**
 * 可选的索引创建、删除与就绪检查能力。
 *
 * <p>索引生命周期是 provider 强相关的：Milvus 的 collection、Qdrant 的 collection、
 * PostgreSQL 的表、Redis 的索引、Weaviate 的 class 等。provider 实现类按能力选择性实现，
 * 业务层通过 {@code instanceof} 检测后再调用，避免对未实现的 provider 报错。</p>
 *
 * <p>本接口只覆盖"骨架"三个方法：
 * <ul>
 *   <li>{@link #createIndex} — 按 {@link VectorProperties.IndexConfig} 创建 schema</li>
 *   <li>{@link #deleteIndex} — 删除整个索引（不可恢复）</li>
 *   <li>{@link #indexExists} — 快速存在性检查，常用于就绪探针</li>
 * </ul>
 * 进阶生命周期能力（list、truncate、update、clone、describe、await ready）由
 * {@code AbstractVectorService} 默认提供 UOE 钩子，由各 provider 自行覆盖。
 *
 * <p>调用关系：
 * <ul>
 *   <li>由 {@code AbstractVectorService} 委托实现</li>
 *   <li>由业务层（运维脚本、初始化命令、就绪探针）通过 {@code instanceof} 调用</li>
 *   <li>由 {@code VectorAutoConfiguration} 在启动阶段用于判断是否需要建索引</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public interface VectorIndexLifecycleOperations {

    /**
     * 创建索引（collection/table/class，provider 术语不一）。
     *
     * <p>使用 {@link VectorProperties.IndexConfig} 中的维度、距离度量、索引类型、副本、
     * 分片等参数。已存在同名索引时 provider 端通常幂等（不抛错）；若业务层需要"严格不存在"
     * 语义，应先调用 {@link #indexExists} 检查。</p>
     *
     * @param indexName 索引名称，非空；命名规则由 provider 限定（如 Milvus 限制
     *                  {@code [a-zA-Z0-9_]}）
     * @param config    索引配置（dimension、metric、indexType、replicas、shards 等）；
     *                  {@code null} 时回退到 provider 默认
     * @throws IllegalArgumentException {@code indexName} 非法或 {@code config} 中
     *                                  dimension 与 model 不匹配时
     */
    void createIndex(String indexName, VectorProperties.IndexConfig config);

    /**
     * 删除整个索引及其下所有数据。
     *
     * <p>不可恢复的破坏性操作。provider 通常对不存在的索引也容忍（不抛错）。</p>
     *
     * @param indexName 索引名称，非空
     * @throws IllegalArgumentException {@code indexName} 为空时
     */
    void deleteIndex(String indexName);

    /**
     * 判断索引是否存在；常用于就绪探针和初始化前置检查。
     *
     * <p>返回 {@code true} 表示该索引已建好 schema 且可被业务层写入；{@code false} 表示
     * 不存在。provider 通常基于 metadata 查询实现，单次调用毫秒级。</p>
     *
     * @param indexName 索引名称，非空
     * @return {@code true} = 存在，{@code false} = 不存在
     * @throws IllegalArgumentException {@code indexName} 为空时
     */
    boolean indexExists(String indexName);
}
