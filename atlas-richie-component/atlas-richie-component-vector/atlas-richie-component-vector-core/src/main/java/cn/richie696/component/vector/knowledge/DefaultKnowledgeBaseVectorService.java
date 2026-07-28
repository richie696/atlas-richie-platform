package cn.richie696.component.vector.knowledge;

import cn.richie696.component.vector.model.SearchOptions;
import cn.richie696.component.vector.model.HybridSearchOptions;
import cn.richie696.component.vector.model.VectorFilter;
import cn.richie696.component.vector.model.VectorSearchResult;
import cn.richie696.component.vector.service.VectorAclAwareHybridSearchOperations;
import cn.richie696.component.vector.service.VectorService;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 默认知识库检索编排：强制 ACL 预过滤、候选池与单文档多样性控制。
 *
 * <p>它是 {@link KnowledgeBaseVectorService} 的唯一标准实现，承担"在拿到原始
 * {@link cn.richie696.component.vector.model.VectorFilter} 之前把所有业务安全/多样性约束
 * 拼好"的责任。三大核心能力：
 * <ul>
 *   <li><b>ACL 预过滤</b> — 把 {@link AccessScope} + {@link DocumentVisibility} 翻译成
 *       provider 能下推的 {@link VectorFilter}；tenantAdmin 跳过 visibility 段</li>
 *   <li><b>projection 版本收敛</b> — 通过可选
 *       {@link ActiveProjectionVersionResolver} 限制"当前对外可见的投影版本"</li>
 *   <li><b>候选池 + 多样性</b> — 用 {@code candidateK} 召回、{@code topK} +
 *       {@code maxChunksPerDocument} 截断，可选 MMR 重排</li>
 * </ul>
 *
 * <p>关键设计取舍：
 * <ul>
 *   <li><b>强制 ACL 在 provider 侧下推</b>：使用 {@link VectorAclAwareHybridSearchOperations}
 *       才能在 hybrid 路径上保证 dense + sparse 召回阶段都受同一 filter 约束，避免
 *       "先召回后过滤"导致稀疏通道绕过 ACL</li>
 *   <li><b>拒绝静默伪造 MMR</b>：MMR 需要每条候选的 embedding 向量，provider 未返回时
 *       抛 {@link UnsupportedOperationException}，而不是用零向量假装重排</li>
 *   <li><b>projection 空集短路</b>：resolver 返回空时直接返回空
 *       {@link RetrievalResult}，避免无意义的 provider 调用</li>
 * </ul>
 *
 * <p>调用关系：
 * <ul>
 *   <li>由网关/Controller 层调用 {@link #search}</li>
 *   <li>本类再委托给 {@link VectorService} 执行召回，
 *       并对结果应用 ACL + 多样性后产出 {@link RetrievalResult}</li>
 *   <li>在 Spring 容器中作为单例存在；无状态、线程安全</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public final class DefaultKnowledgeBaseVectorService implements KnowledgeBaseVectorService {

    /**
     * provider 句柄；用于执行 dense 检索或 hybrid 检索（后者要求 provider 同时实现
     * {@link VectorAclAwareHybridSearchOperations}）。
     */
    private final VectorService vectorService;

    /**
     * 可选的 projection 版本解析器；用于在多版本投影场景下限制"当前可见"的版本。
     * 为 {@code null} 时跳过 projection 过滤（等同于单版本语义）。
     */
    private final ActiveProjectionVersionResolver activeProjectionVersionResolver;

    /**
     * 仅使用 {@link VectorService} 的简化构造器；适用于无 projection 版本管理的部署。
     *
     * <p>等价于 {@code this(vectorService, null)}。</p>
     *
     * @param vectorService provider 句柄；运行时由 Spring 容器注入
     */
    public DefaultKnowledgeBaseVectorService(VectorService vectorService) {
        this(vectorService, null);
    }

    /**
     * 完整构造器，注入 {@link ActiveProjectionVersionResolver}。
     *
     * <p>resolver 为 {@code null} 时跳过 projection 版本过滤；调用方可在测试或简化场景下
     * 使用上一构造器。</p>
     *
     * @param vectorService                   provider 句柄
     * @param activeProjectionVersionResolver 可选的 projection 版本解析器；为 {@code null}
     *                                        表示不过滤 projection 版本
     */
    public DefaultKnowledgeBaseVectorService(VectorService vectorService,
                                             ActiveProjectionVersionResolver activeProjectionVersionResolver) {
        this.vectorService = vectorService;
        this.activeProjectionVersionResolver = activeProjectionVersionResolver;
    }

    /**
     * {@inheritDoc}
     *
     * <p>本实现的执行步骤：
     * <ol>
     *   <li>校验 {@code knowledgeBaseId} 非空</li>
     *   <li>构造 ACL filter（含 tenantId/knowledgeBaseId/status + visibility 决策树），
     *       并 AND 上 {@code additionalFilter}（或 {@code tenantId exists} 兜底）</li>
     *   <li>若注入 resolver，附加 {@code projectionVersionId IN (active)}；空集短路</li>
     *   <li>根据 {@code request.hybrid()} 选择 hybrid 或 dense 检索</li>
     *   <li>对候选应用 MMR 与单文档多样性截断，产出 {@link RetrievalCitation} 列表</li>
     * </ol>
     *
     * @throws IllegalArgumentException       {@code knowledgeBaseId} 为空时
     * @throws UnsupportedOperationException  请求 hybrid 但 provider 未实现
     *                                         {@link VectorAclAwareHybridSearchOperations}，
     *                                         或 MMR 模式下候选缺失 embedding 向量时
     */
    @Override
    public RetrievalResult search(String knowledgeBaseId, KnowledgeSearchRequest request) {
        if (knowledgeBaseId == null || knowledgeBaseId.isBlank()) {
            throw new IllegalArgumentException("knowledgeBaseId must not be blank");
        }
        Instant started = Instant.now();
        VectorFilter filter = accessFilter(knowledgeBaseId, request.accessScope());
        if (request.additionalFilter() != null) {
            filter = VectorFilter.and(filter, request.additionalFilter());
        }
        if (activeProjectionVersionResolver != null) {
            var activeVersions = activeProjectionVersionResolver.activeVersionIds(request.accessScope().tenantId(), knowledgeBaseId);
            if (activeVersions.isEmpty()) {
                return new RetrievalResult(List.of(), new RetrievalDiagnostics(0, 0, request.hybrid(), request.rerank(),
                        Duration.between(started, Instant.now())));
            }
            filter = VectorFilter.and(filter, VectorFilter.in("projectionVersionId", activeVersions));
        }
        SearchOptions options = SearchOptions.builder().filter(filter).rerank(request.rerank()).build();
        List<VectorSearchResult> candidates;
        if (request.hybrid()) {
            if (!(vectorService instanceof VectorAclAwareHybridSearchOperations hybrid)) {
                throw new UnsupportedOperationException("configured vector provider does not support ACL-safe hybrid search");
            }
            candidates = hybrid.hybridSearch(knowledgeBaseId, request.query(), request.keywordQuery(),
                    request.candidateK(), HybridSearchOptions.builder().searchOptions(options)
                            .keywordQuery(request.keywordQuery()).build(), filter);
        } else {
            candidates = vectorService.searchByText(knowledgeBaseId, request.query(), request.candidateK(), options);
        }
        List<RetrievalCitation> citations = diversify(candidates, request.topK(), request.maxChunksPerDocument(),
                request.mmr(), request.mmrLambda());
        return new RetrievalResult(citations, new RetrievalDiagnostics(candidates.size(), citations.size(), request.hybrid(),
                request.rerank(), Duration.between(started, Instant.now())));
    }

    /**
     * 构造 ACL 预过滤的 {@link VectorFilter}。
     *
     * <p>包含三段必选断言：{@code tenantId = ?}、{@code knowledgeBaseId = ?}、
     * {@code status = "ACTIVE"}；非 tenantAdmin 用户再追加 visibility 决策树：
     * <ul>
     *   <li>{@link DocumentVisibility#COMPANY} 始终命中</li>
     *   <li>{@link DocumentVisibility#DEPARTMENT} ∩ 主体部门</li>
     *   <li>{@link DocumentVisibility#CUSTOM} ∩ 主体部门 / 主体</li>
     *   <li>{@link DocumentVisibility#PRIVATE} ∩ 主体</li>
     * </ul>
     * 决策树以 {@code OR} 形式拼接，任一分支命中即视为可见。空部门/账号集合时
     * 对应分支自然不命中 — 这是有意的安全降级而非 bug。</p>
     *
     * @param knowledgeBaseId 知识库 ID，用于构造 {@code knowledgeBaseId = ?} 子句
     * @param scope           调用主体范围；{@code tenantAdmin=true} 时跳过 visibility 段
     * @return 复合 {@link VectorFilter}，可下推到 provider
     */
    private VectorFilter accessFilter(String knowledgeBaseId, AccessScope scope) {
        List<VectorFilter> mandatory = new ArrayList<>();
        mandatory.add(VectorFilter.eq("tenantId", scope.tenantId()));
        mandatory.add(VectorFilter.eq("knowledgeBaseId", knowledgeBaseId));
        mandatory.add(VectorFilter.eq("status", "ACTIVE"));
        if (!scope.tenantAdmin()) {
            List<VectorFilter> visibility = new ArrayList<>();
            visibility.add(VectorFilter.eq("visibility", DocumentVisibility.COMPANY.name()));
            if (!scope.departmentIds().isEmpty()) {
                visibility.add(VectorFilter.and(VectorFilter.eq("visibility", DocumentVisibility.DEPARTMENT.name()),
                        VectorFilter.containsAny("allowedDepartmentIds", scope.departmentIds())));
                visibility.add(VectorFilter.and(VectorFilter.eq("visibility", DocumentVisibility.CUSTOM.name()),
                        VectorFilter.containsAny("allowedDepartmentIds", scope.departmentIds())));
            }
            if (!scope.principalIds().isEmpty()) {
                visibility.add(VectorFilter.and(VectorFilter.eq("visibility", DocumentVisibility.CUSTOM.name()),
                        VectorFilter.containsAny("allowedPrincipalIds", scope.principalIds())));
                visibility.add(VectorFilter.and(VectorFilter.eq("visibility", DocumentVisibility.PRIVATE.name()),
                        VectorFilter.containsAny("allowedPrincipalIds", scope.principalIds())));
            }
            mandatory.add(VectorFilter.or(visibility.toArray(VectorFilter[]::new)));
        }
        return VectorFilter.and(mandatory.toArray(VectorFilter[]::new));
    }

    /**
     * 对候选做"按文档多样性"的截断，可选叠加 MMR 重排。
     *
     * <p>两阶段：
     * <ol>
     *   <li>若 {@code mmr=true}，先用 {@link #mmrOrder} 按 MMR 重排</li>
     *   <li>按重排后的顺序遍历候选，按 {@code documentId}（缺省回退为
     *       {@code vectorId}）累计单文档入选项数；超过 {@code maxPerDocument} 跳过；
     *       累计到 {@code topK} 时终止</li>
     * </ol>
     *
     * @param candidates        provider 返回的候选（已应用 ACL filter）
     * @param topK              最终返回条数上限
     * @param maxPerDocument    单文档最大入选项数，控制"单文档霸榜"
     * @param mmr               是否启用 MMR 重排
     * @param mmrLambda         MMR 相关性/冗余度权重
     * @return 最终引用的 {@link RetrievalCitation} 列表，长度 {@code <= topK}
     */
    private List<RetrievalCitation> diversify(List<VectorSearchResult> candidates, int topK, int maxPerDocument,
                                              boolean mmr, double mmrLambda) {
        List<VectorSearchResult> ordered = mmr ? mmrOrder(candidates, mmrLambda) : candidates;
        Map<String, Integer> documentCounts = new java.util.HashMap<>();
        List<RetrievalCitation> result = new ArrayList<>(topK);
        for (VectorSearchResult candidate : ordered) {
            Map<String, Object> metadata = metadata(candidate.getMetadata());
            String documentId = stringMetadata(metadata, "documentId");
            String countKey = documentId == null ? candidate.getId() : documentId;
            if (documentCounts.getOrDefault(countKey, 0) >= maxPerDocument) continue;
            documentCounts.merge(countKey, 1, Integer::sum);
            result.add(new RetrievalCitation(candidate.getId(), documentId, integerMetadata(metadata, "chunkNo"),
                    candidate.getContent(), candidate.getScore() == null ? 0.0 : candidate.getScore(), metadata));
            if (result.size() == topK) break;
        }
        return result;
    }

    /**
     * 基于已返回向量的标准 MMR；provider 未返回向量时拒绝静默伪造 MMR。
     *
     * <p>贪心策略：每一轮从剩余候选中选
     * {@code score = lambda * relevance - (1 - lambda) * max cosine(chosen)}，
     * 直到候选耗尽。时间复杂度 {@code O(n^2 * d)}（{@code d} = 向量维度），
     * 适用于 {@code candidateK} 在百级以内的常见场景。</p>
     *
     * @param candidates provider 返回的候选，必须都携带非空 {@code vector} 字段
     * @param lambda     相关性权重；{@code 0} 表示纯多样性，{@code 1} 表示纯相关性
     * @return 按 MMR 排序后的候选
     * @throws UnsupportedOperationException 当任一候选缺失 embedding 向量时抛出
     */
    private List<VectorSearchResult> mmrOrder(List<VectorSearchResult> candidates, double lambda) {
        if (candidates.stream().anyMatch(item -> item.getVector() == null || item.getVector().length == 0)) {
            throw new UnsupportedOperationException("MMR requires provider results to include embedding vectors");
        }
        List<VectorSearchResult> remaining = new ArrayList<>(candidates);
        List<VectorSearchResult> selected = new ArrayList<>(candidates.size());
        while (!remaining.isEmpty()) {
            VectorSearchResult best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (VectorSearchResult candidate : remaining) {
                double relevance = candidate.getScore() == null ? 0.0 : candidate.getScore();
                double redundancy = selected.stream().mapToDouble(chosen -> cosine(candidate.getVector(), chosen.getVector()))
                        .max().orElse(0.0);
                double score = lambda * relevance - (1.0 - lambda) * redundancy;
                if (score > bestScore) { best = candidate; bestScore = score; }
            }
            selected.add(best);
            remaining.remove(best);
        }
        return selected;
    }

    /**
     * 余弦相似度，按较短向量的长度做截断；任一向量为零向量时返回 0，避免
     * {@code 0/0} 给出 {@code NaN}。
     *
     * @param left  候选向量
     * @param right 已选向量
     * @return {@code [0, 1]} 范围的余弦相似度
     */
    private static double cosine(float[] left, float[] right) {
        double dot = 0, leftNorm = 0, rightNorm = 0;
        for (int i = 0; i < left.length && i < right.length; i++) {
            dot += left[i] * right[i]; leftNorm += left[i] * left[i]; rightNorm += right[i] * right[i];
        }
        return leftNorm == 0 || rightNorm == 0 ? 0 : dot / Math.sqrt(leftNorm * rightNorm);
    }

    /**
     * 把 {@code VectorSearchResult.getMetadata()} 规整为不可变 {@code Map<String, Object>}。
     * 非 Map 类型或为 {@code null} 时回退为 {@link Map#of()}。
     *
     * @param metadata provider 返回的元数据
     * @return 不可变 Map 视图
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> metadata(Object metadata) {
        return metadata instanceof Map<?, ?> map ? (Map<String, Object>) Map.copyOf((Map<?, ?>) map) : Map.of();
    }

    /**
     * 从 metadata 中按 key 取字符串值；缺失返回 {@code null}。
     */
    private static String stringMetadata(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 从 metadata 中按 key 取整数值；优先 {@code Number}，否则尝试字符串转换；
     * 转换失败返回 {@code null}。
     */
    private static Integer integerMetadata(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number number) return number.intValue();
        try { return value == null ? null : Integer.valueOf(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return null; }
    }
}
