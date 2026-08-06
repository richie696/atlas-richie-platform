package cn.richie696.component.vector.service;

/**
 * 可选的 provider 原生索引别名能力。
 *
 * <p>它是"蓝绿发布"和"零停机重建索引"场景的核心：业务方始终访问 {@code alias} 名，
 * 底层可在不感知业务的情况下把 alias 指向新索引。当前仅 Milvus 原生支持 alias，
 * 其他 provider 通过 throwUnsupportedOps 抛出 {@link UnsupportedOperationException}。</p>
 *
 * <p>调用关系：
 * <ul>
 *   <li>由 {@code AbstractVectorService} 委托实现</li>
 *   <li>由业务层（重建索引脚本、灰度发布流水线）通过 {@code instanceof} 调用</li>
 *   <li>{@link #switchAlias} 通常和
 *       {@link cn.richie696.component.vector.service.impl.AbstractVectorService#awaitIndexReady(String, java.time.Duration)}
 *       配合使用：先建新索引、等 ready、再切 alias</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public interface VectorIndexAliasOperations {

    /**
     * 在 {@code indexName} 上创建别名 {@code alias}。
     *
     * <p>后续对 {@code alias} 的读写等价于对 {@code indexName} 的操作。
     * Milvus 原生支持；Qdrant / PostgreSQL / Redis / MongoDB / Neo4j / Weaviate 未实现，
     * 会通过 throwUnsupportedOps 抛 {@link UnsupportedOperationException}。</p>
     *
     * @param indexName 索引名称，非空
     * @param alias     别名，非空；命名约束同索引名
     * @return {@code true} = 创建成功；{@code false} = provider 报告失败
     * （已存在的别名、名称冲突等场景由 provider 决定返回 false 还是抛错）
     * @throws UnsupportedOperationException provider 不支持时抛出
     * @throws IllegalArgumentException      {@code indexName} 或 {@code alias} 为空时
     */
    boolean createAlias(String indexName, String alias);

    /**
     * 把别名 {@code alias} 从 {@code oldIndexName} 原子切到 {@code newIndexName}。
     *
     * <p>"原子"由 provider 端保证（Milvus 的 {@code alterAlias} 内部用事务），
     * 调用方仍应在切换前后用 {@code awaitIndexReady} 等待新索引就绪，避免 alias 切
     * 过去但新索引尚未 ready 导致瞬时空命中。</p>
     *
     * @param oldIndexName 当前指向别名 {@code alias} 的索引；非空
     * @param newIndexName 新目标索引；非空，且应当已经 ready
     * @param alias        待切换的别名；非空
     * @return {@code true} = 切换成功；{@code false} = 切换失败
     * @throws UnsupportedOperationException provider 不支持时抛出
     * @throws IllegalArgumentException      任一参数为空时
     */
    boolean switchAlias(String oldIndexName, String newIndexName, String alias);
}
