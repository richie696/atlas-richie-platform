package cn.richie696.component.vector.service;

import cn.richie696.component.vector.model.HybridSearchOptions;
import cn.richie696.component.vector.model.VectorSearchResult;

import java.util.List;

/**
 * 可选的原生或明确实现的混合检索能力。
 *
 * <p>它是"向量召回 + 关键词召回"在同一请求内融合检索的接口。vector 维度（dense）
 * 和 keyword 维度（sparse / BM25）通过 {@link HybridSearchOptions} 配置权重与
 * 内层 {@link cn.richie696.component.vector.model.SearchOptions}。</p>
 *
 * <p>关键约束：
 * <ul>
 *   <li>本接口不接受 {@link cn.richie696.component.vector.model.VectorFilter} —
 *       业务方如果需要"在 hybrid 召回阶段下推 ACL filter"，必须使用
 *       {@link VectorAclAwareHybridSearchOperations}</li>
 *   <li>{@link HybridSearchOptions} 中的 {@code vectorWeight} + {@code keywordWeight}
 *       总和无需严格等于 {@code 1.0}，但通常归一化到 {@code [0, 1]}</li>
 *   <li>{@code text} 必填；{@code keywordQuery} 可空（部分 provider 会退化为仅 dense）</li>
 * </ul>
 *
 * <p>调用关系：
 * <ul>
 *   <li>由 {@code AbstractVectorService} 默认实现为"退化为 dense 检索" —
 *       {@code @code AbstractVectorService#hybridSearchImpl} 默认抛
 *       {@link UnsupportedOperationException} 并显式提示"不要静默降级"</li>
 *   <li>由 {@link cn.richie696.component.vector.knowledge.DefaultKnowledgeBaseVectorService}
 *       在 {@code KnowledgeSearchRequest.hybrid == true} 但 provider 未实现
 *       {@link VectorAclAwareHybridSearchOperations} 时检测并报错</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public interface VectorHybridSearchOperations {

    /**
     * 在指定索引上执行 dense + sparse 融合检索。
     *
     * <p>融合方式由 provider 决定：可能是 RRF（Reciprocal Rank Fusion）、加权求和或
     * provider 私有算法。返回的 {@link VectorSearchResult#score} 为融合后的标量，
     * 含义与纯 dense 检索不完全一致，业务层不应跨通道直接比较 score 大小。</p>
     *
     * @param indexName    索引名称，非空
     * @param text         dense 通道查询文本，非空
     * @param keywordQuery sparse 通道查询关键词；为 {@code null} 时部分 provider 退化为
     *                     仅 dense
     * @param limit        返回条数上限
     * @param options      混合选项（权重、内层 SearchOptions、keyword query 等）；
     *                     {@code null} 时回退到默认权重 0.7/0.3 + 空 SearchOptions
     * @return 融合后的命中候选；可能为空但不为 {@code null}
     * @throws IllegalArgumentException      {@code indexName} 或 {@code text} 为空时
     * @throws UnsupportedOperationException provider 实际不支持时（由 {@code AbstractVectorService}
     *                                       默认实现显式抛出，避免静默降级）
     */
    List<VectorSearchResult> hybridSearch(String indexName, String text, String keywordQuery,
                                          int limit, HybridSearchOptions options);
}
