package cn.richie696.component.vector.service;

import cn.richie696.component.vector.model.IndexInfo;

import java.util.List;

/**
 * 可选的索引统计与观测能力。
 *
 * <p>它是向量中台对"我能看到哪些索引、索引里有多少条、状态怎样、健康度如何"的统一接口。
 * 不同 provider 在这些方法上的实现成本差异很大：Milvus/Qdrant 提供原生
 * {@code describe/num_entities}，PostgreSQL 需要 {@code SELECT count(*)}，Redis 需要
 * {@code FT.INFO}。业务层应假定 {@code getIndexStats} 返回的 {@link IndexInfo} 是
 * provider 视角的"近似"快照，不应强依赖跨 provider 一致。</p>
 *
 * <p>调用关系：
 * <ul>
 *   <li>由 {@code AbstractVectorService} 委托实现</li>
 *   <li>由业务层（运维看板、健康探针、容量规划）通过 {@code instanceof} 调用</li>
 *   <li>{@link #healthCheck} 是 Spring Boot Actuator HealthIndicator 的常用接入点</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public interface VectorIndexStatsOperations {

    /**
     * 统计索引内文档总数。
     *
     * <p>provider 实现差异：Milvus 提供精确计数（{@code num_entities}），Qdrant 通过
     * 全量迭代给出近似，PostgreSQL 取决于是否启用 {@code ANALYZE}。返回值不带强实时
     * 保证 — 与写入存在毫秒到秒级延迟。</p>
     *
     * @param indexName 索引名称，非空
     * @return 文档总数；{@code 0} 表示空索引；非正数视为异常
     * @throws IllegalArgumentException {@code indexName} 为空时
     */
    long countDocuments(String indexName);

    /**
     * 列出当前 provider 实例下所有可见索引。
     *
     * <p>主要用于运维/盘点。返回的 {@link IndexInfo} 至少携带 name、modality、dimension、
     * metric、status、documentCount；其余字段（timestamp、provider metadata）由 provider 决定。</p>
     *
     * @return 索引列表；空集合表示 provider 上没有任何索引
     */
    List<IndexInfo> listIndexes();

    /**
     * 获取单个索引的详细快照（包含文档数、维度、metric、status、时间戳等）。
     *
     * <p>比 {@link #listIndexes} 的列表元素更详细，是单点运维排障的主要接口。
     * 返回的 {@link IndexInfo} 不可变，可安全传给前端。</p>
     *
     * @param indexName 索引名称，非空
     * @return 索引快照；provider 不存在该索引时抛 {@link IllegalArgumentException}
     * @throws IllegalArgumentException {@code indexName} 为空或不存在时
     */
    IndexInfo getIndexStats(String indexName);

    /**
     * 索引健康检查。
     *
     * <p>{@code AbstractVectorService.healthCheckImpl} 默认实现是三步探针：
     * {@code indexExists → countDocuments >= 0 → 不抛异常}。provider 可重写以加入更
     * 精细的判定（如分片可用性、副本同步状态）。任意检查失败整体返回 {@code false}，
     * 不向调用方抛异常，便于直接接入 actuator/探针。</p>
     *
     * @param indexName 索引名称，非空
     * @return {@code true} = 健康，{@code false} = 任一检查失败
     */
    boolean healthCheck(String indexName);
}
