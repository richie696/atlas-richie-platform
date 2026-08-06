package cn.richie696.component.vector.service;

import cn.richie696.component.vector.model.HybridSearchOptions;
import cn.richie696.component.vector.model.VectorFilter;
import cn.richie696.component.vector.model.VectorSearchResult;

import java.util.List;

/**
 * 只有能在 hybrid 候选召回阶段下推结构化过滤的 provider 才能实现此能力。
 *
 * <p>它继承 {@link VectorHybridSearchOperations}，额外要求"在 dense + sparse 召回阶段
 * 都能下推同一个 {@link VectorFilter}"。这是知识库 ACL 安全模型的硬约束 — 如果 filter
 * 只能在 dense 召回后过滤，sparse 通道就可能召回出 ACL 不允许的文档片段并泄漏给上层。
 * 因此 {@link cn.richie696.component.vector.knowledge.DefaultKnowledgeBaseVectorService}
 * 在 hybrid 模式下会通过 {@code instanceof} 严格挑选实现此接口的 provider，否则抛
 * {@link UnsupportedOperationException}。</p>
 *
 * <p>当前只有 Milvus / Qdrant 等少数 provider 同时支持 hybrid + filter 下推；其他 provider
 * 应通过 hybridSearchImpl 的 UOE 默认行为显式拒绝 hybrid 调用，而不是退化为 dense + 后过滤。</p>
 *
 * <p>调用关系：
 * <ul>
 *   <li>由 {@link cn.richie696.component.vector.knowledge.DefaultKnowledgeBaseVectorService}
 *       在 {@code KnowledgeSearchRequest.hybrid == true} 时检测并使用</li>
 *   <li>本接口既是父接口 {@link VectorHybridSearchOperations} 的扩展，又是 provider
 *       的"安全等级"声明</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public interface VectorAclAwareHybridSearchOperations extends VectorHybridSearchOperations {

    /**
     * 在 dense + sparse 召回阶段同时下推 {@link VectorFilter} 的混合检索。
     *
     * <p>{@code filter} 与 {@code options.searchOptions().getFilter()} 表达同一过滤意图，
     * 但必须同时传入：前者用于本接口的 ACL 硬约束（即使 {@code options} 不携带 filter，
     * 调用方也会传入独立的 {@code filter} 参数），后者用于 provider 内部生成 DSL 表达式
     * 并复用过滤以外的设置（rerank、namespace 等）。两个 filter 语义一致时由
     * {@link cn.richie696.component.vector.knowledge.DefaultKnowledgeBaseVectorService}
     * 保证不会出现冲突。</p>
     *
     * @param indexName    索引名称，非空
     * @param text         dense 通道查询文本，非空
     * @param keywordQuery sparse 通道查询关键词；为 {@code null} 时退化为仅 dense
     * @param limit        返回条数上限
     * @param options      混合选项（权重、keyword query、内层 SearchOptions）
     * @param filter       ACL 预过滤条件；非空，由调用方在
     *                     {@link cn.richie696.component.vector.knowledge.DefaultKnowledgeBaseVectorService#search}
     *                     中构造（tenantId + knowledgeBaseId + status + visibility 决策树）
     * @return 融合后的命中候选（已应用 filter）
     * @throws IllegalArgumentException      参数为空时
     * @throws UnsupportedOperationException provider 未实现 ACL 下推时（不应被调用到 —
     *                                       业务层通过 {@code instanceof} 守卫）
     */
    List<VectorSearchResult> hybridSearch(String indexName, String text, String keywordQuery, int limit,
                                          HybridSearchOptions options, VectorFilter filter);
}
