package cn.richie696.component.vector.knowledge;

import cn.richie696.component.vector.model.VectorFilter;

/**
 * 商用知识库的统一检索请求。
 *
 * <p>它是 {@link KnowledgeBaseVectorService#search} 的入参载体，封装了语义检索所需的全部
 * 调谐参数：召回量、返回量、ACL 范围、可选重排、可选混合、可选 MMR 多样性。
 * 所有合法性在紧凑构造器中一次校验，确保 {@link DefaultKnowledgeBaseVectorService}
 * 在拿到对象之后不必再做空值/越界判断；默认值也在这里集中给出。</p>
 *
 * <p>关键不变量：
 * <ul>
 *   <li>{@code query} 必填，空白字符视为非法</li>
 *   <li>{@code topK &gt; 0}，{@code candidateK &ge; topK}，避免"MMR 之前没有候选"</li>
 *   <li>{@code candidateK} 缺省时取 {@code max(topK, 50)}，保证有足够候选做多样性</li>
 *   <li>{@code maxChunksPerDocument} 缺省时为 {@code 2}，对应经验值"同一文档最多 2 个
 *       chunk 进入结果"，避免单一文档霸榜</li>
 *   <li>{@code mmrLambda} 必须在 {@code [0, 1]}：{@code 1.0} = 纯相关性，
 *       {@code 0.0} = 纯多样性，{@code 0.6} 左右是常用折中</li>
 * </ul>
 *
 * @param query               语义查询文本；非空、不可全空白字符
 * @param topK                最终返回条数上限，必须 {@code &gt; 0}
 * @param candidateK          第一轮召回条数（候选池大小），必须 {@code &ge; topK}；
 *                            缺省时取 {@code max(topK, 50)}
 * @param accessScope         调用主体范围（ACL 预过滤的唯一权威输入），必填；
 *                            详见 {@link AccessScope}
 * @param rerank              是否在文本检索后调用重排序；图像检索默认不重排
 * @param hybrid              是否走 hybrid（dense + sparse）路径；为 {@code true} 时
 *                            provider 必须实现
 *                            {@link cn.richie696.component.vector.service.VectorAclAwareHybridSearchOperations}
 * @param keywordQuery        显式关键词查询，用于 BM25/sparse 通道；
 *                            当 {@code hybrid=false} 时可传 {@code null}，
 *                            当 {@code hybrid=true} 时若缺省则退化为 {@code query}
 * @param mmr                 是否启用 MMR 多样性重排；
 *                            要求 provider 在检索结果里携带 {@code vector} 字段，
 *                            否则 {@link DefaultKnowledgeBaseVectorService} 会拒绝
 *                            静默伪造（抛 {@link UnsupportedOperationException}）
 * @param mmrLambda           MMR 相关性/冗余度权重；{@code [0, 1]}，常用 {@code 0.6}
 * @param maxChunksPerDocument 单文档最大入选项数；{@code &le; 0} 时取 {@code 2}；
 *                             控制"单文档霸榜"
 * @param additionalFilter    业务侧追加的过滤条件；为 {@code null} 时由
 *                            {@link DefaultKnowledgeBaseVectorService} 替换为
 *                            {@code VectorFilter.exists("tenantId")} 兜底断言
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public record KnowledgeSearchRequest(String query, int topK, int candidateK, AccessScope accessScope,
                                     boolean rerank, boolean hybrid, String keywordQuery,
                                     boolean mmr, double mmrLambda, int maxChunksPerDocument, VectorFilter additionalFilter) {
    /**
     * 紧凑构造器：执行所有字段的合法性校验和默认值规范化。
     *
     * <p>一旦构造成功，{@link DefaultKnowledgeBaseVectorService} 在使用时可直接读取所有
     * 字段而无需再校验。校验失败抛 {@link IllegalArgumentException}，业务层应在外层拦截
     * 并返回 4xx，而非让异常穿透到上游。</p>
     *
     * @throws IllegalArgumentException 当 {@code query} 为空、{@code topK} 非正、
     *                                  {@code candidateK < topK}、{@code accessScope} 为空、
     *                                  {@code mmrLambda} 越界时抛出
     */
    public KnowledgeSearchRequest {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query must not be blank");
        if (topK <= 0) throw new IllegalArgumentException("topK must be positive");
        candidateK = candidateK <= 0 ? Math.max(topK, 50) : candidateK;
        if (candidateK < topK) throw new IllegalArgumentException("candidateK must be >= topK");
        if (accessScope == null) throw new IllegalArgumentException("accessScope must not be null");
        if (mmrLambda < 0.0 || mmrLambda > 1.0) throw new IllegalArgumentException("mmrLambda must be between 0 and 1");
        maxChunksPerDocument = maxChunksPerDocument <= 0 ? 2 : maxChunksPerDocument;
    }
}
