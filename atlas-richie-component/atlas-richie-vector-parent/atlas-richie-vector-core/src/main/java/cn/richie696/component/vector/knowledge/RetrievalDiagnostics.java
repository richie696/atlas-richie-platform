package cn.richie696.component.vector.knowledge;

import java.time.Duration;

/**
 * 检索阶段诊断，不暴露 provider 私有请求或敏感 ACL 内容。
 *
 * <p>它是 {@link RetrievalResult#diagnostics()} 的承载类型，被设计为"可对业务用户展示"
 * 的轻量诊断：候选数、返回数、是否走混合/重排路径、耗时。设计上明确"不"承载：
 * <ul>
 *   <li>原始查询字符串、provider 私有 DSL 表达式 — 避免日志/审计泄露</li>
 *   <li>{@link AccessScope} 的部门/账号集合 — 避免权限标签落到引用回执里</li>
 *   <li>{@code VectorFilter} 的细节结构 — 避免外部依赖 provider filter 表达</li>
 * </ul>
 *
 * <p>{@code candidateCount} 与 {@code returnedCount} 之差通常用于衡量多样性截断的力度：
 * 当 {@code candidateCount &gt;&gt; returnedCount} 时说明 MMR 或 {@code maxChunksPerDocument}
 * 大量生效；当两者接近时说明候选池本身就稀疏，可提示用户"扩大召回"。</p>
 *
 * @param candidateCount 第一轮召回的候选数（已应用 ACL + 投影版本过滤，但未做多样性
 *                       截断），用于评估召回质量
 * @param returnedCount  最终返回的 {@link RetrievalCitation} 数量，受 {@code topK} 与
 *                       {@code maxChunksPerDocument} 双重限制
 * @param hybrid         实际走的检索路径是否为 hybrid（dense + sparse）；
 *                       失败回退到 dense 时也会如实标记为 {@code false}
 * @param reranked       实际是否调用了重排序；rerank 服务调用失败时会保持 {@code false}，
 *                       并由 {@code tryRerank} 内部日志记录（不写入 diagnostics 以避免噪声）
 * @param elapsed        整个 {@link KnowledgeBaseVectorService#search} 调用的端到端耗时，
 *                       包含 ACL filter 构造、projection 版本解析、provider 调用、
 *                       MMR 多样性截断
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public record RetrievalDiagnostics(int candidateCount, int returnedCount, boolean hybrid, boolean reranked,
                                   Duration elapsed) {
}
