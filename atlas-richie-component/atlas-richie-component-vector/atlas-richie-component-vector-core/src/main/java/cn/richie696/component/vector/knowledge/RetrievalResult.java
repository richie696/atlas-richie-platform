package cn.richie696.component.vector.knowledge;

import java.util.List;

/**
 * 知识库检索结果的统一承载类型。
 *
 * <p>它将检索命中的引用与诊断信息组合为上层知识服务的返回值，
 * 使调用方能够同时消费业务结果和检索过程元数据。</p>
 *
 * <p>关键不变量：
 * <ul>
 *   <li>{@code citations} 在紧凑构造器中通过 {@link List#copyOf} 拷贝为不可变视图，
 *       业务层不能通过 {@code getCitations().add(...)} 改写结果</li>
 *   <li>{@code citations} 列表里每条 {@link RetrievalCitation} 都已通过 ACL 校验；
 *       业务层可直接用作 RAG 引用</li>
 *   <li>{@code diagnostics} 始终存在 — 即使候选数为 0 也返回带
 *       {@code candidateCount=0} 的诊断，避免下游因 null 检查带来的样板</li>
 * </ul>
 *
 * <p>调用关系：
 * <ul>
 *   <li>由 {@link KnowledgeBaseVectorService#search} 产出</li>
 *   <li>由业务层（问答/智能体/RAG 服务）作为返回值消费</li>
 *   <li>其中的 {@code diagnostics} 字段用于驱动 UI 提示（"无相关结果，建议换关键词"）或
 *       A/B 实验指标采集</li>
 * </ul>
 *
 * @param citations   检索命中的引用列表；紧凑构造器中已防御性拷贝为不可变 List
 * @param diagnostics 本次检索的诊断信息，候选数为 0 时仍会带
 *                   {@link RetrievalDiagnostics#candidateCount()}=0 的诊断
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public record RetrievalResult(List<RetrievalCitation> citations, RetrievalDiagnostics diagnostics) {
    /**
     * 紧凑构造器：对 {@code citations} 做防御性拷贝，避免下游修改污染共享结果。
     *
     * <p>{@code diagnostics} 字段不做拷贝 — 它本身是不可变 record，再拷贝无意义。</p>
     */
    public RetrievalResult { citations = List.copyOf(citations); }
}
