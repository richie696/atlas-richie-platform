package cn.richie696.component.vector.knowledge;

import java.util.Map;

/**
 * 可直接用于回答引用展示的检索命中。
 *
 * <p>它是 {@link RetrievalResult#citations()} 的元素类型，由
 * {@link DefaultKnowledgeBaseVectorService} 在"通过 ACL filter → 召回候选 → MMR/多样性截断"
 * 三步之后产出。每个 {@code RetrievalCitation} 都已经通过 ACL 校验，业务层可直接用作
 * RAG 回答中的 [1][2][3] 引用，附上 {@code documentId} + {@code chunkNo} 做"打开原文"
 * 跳转。</p>
 *
 * <p>{@code metadata} 字段透传了 {@code VectorRecord} 的 metadata（拷贝后的不可变视图），
 * 其中 {@code documentId}/{@code chunkNo} 被同时提为顶层字段，方便 JSON 序列化时避免
 * 在 metadata 子对象里翻找；其它元数据（如 {@code source}、{@code tags}、{@code version}）
 * 仍以 metadata 形式存在。</p>
 *
 * <p>{@code score} 含义取决于路径：纯 dense 检索时为向量相似度（通常为余弦，已归一化
 * 到 [0, 1]）；hybrid 检索时为融合后的标量；rerank 开启时为重排分数。
 * 业务层在展示时应避免直接断言"score 越接近 1 越好"等不严谨语义。</p>
 *
 * @param vectorId    provider 分配的向量主键；用于
 *                    {@link cn.richie696.component.vector.service.VectorRecordReadOperations#getById}
 *                    精确定位原文
 * @param documentId  业务文档 ID（来自 metadata/documentId）；用于跨 chunk 聚合同一文档
 *                    以及"打开原文"链接；当 {@code VectorRecord.metadata.documentId}
 *                    缺失时回退为 {@code vectorId}
 * @param chunkNo     chunk 在原文档中的序号（来自 metadata/chunkNo）；为 {@code null}
 *                    时表示 provider 未返回该字段或该文档未被切分
 * @param content     命中片段文本内容；可直接贴入回答或留作截断展示
 * @param score       命中分值，含义随检索路径变化；dense 时为相似度，hybrid 时为融合分
 * @param metadata    与 {@code VectorRecord.metadata} 等价的不可变拷贝（已
 *                    {@link Map#copyOf} 防御性拷贝），保留 {@code source/tags/version/...}
 *                    等业务字段
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public record RetrievalCitation(String vectorId, String documentId, Integer chunkNo, String content,
                                double score, Map<String, Object> metadata) {
}
