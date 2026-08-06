package cn.richie696.component.vector.chunk;

import java.util.Map;

/**
 * 把一个文档切分为 chunk 后，向 chunk 级别的向量记录透传文档级上下文。
 *
 * <p>{@link ChunkVectorRecordMapper} 在把 {@link cn.richie696.component.chunking.model.Chunk}
 * 转换为 {@link cn.richie696.component.vector.model.VectorRecord} 时使用本 record 作为上下文载体：
 * 文档级的 index / documentId / version / namespace / metadata 由本对象注入，chunk 级的偏移与序号
 * 由 chunk 自身提供。这样既保留文档粒度的版本与命名空间信息，又保证每条 chunk 向量记录在向量库
 * 内拥有稳定且唯一的 ID，便于按文档版本做批量重写或按 namespace 实现多租户隔离。</p>
 *
 * <p>本 record 由调用方在切分完成后、写入向量前一次性构造；本模块不调用任何向量服务。</p>
 *
 * @param indexName  文档所属的向量索引名，最终成为 VectorRecord 的 indexName 字段
 * @param documentId 文档级唯一标识，会参与每条 chunk 向量 ID 的拼接以保证跨 chunk 唯一
 * @param version    文档版本号，用于区分同一 documentId 的多次重写（如 RAG 文档再嵌入场景）
 * @param namespace  多租户或分组命名空间，会透传到 VectorRecord 的 namespace 字段
 * @param metadata   文档级元数据，与 chunk 级元数据合并后再写入 VectorRecord 的 metadata
 */
public record VectorRecordContext(String indexName, String documentId, Long version, String namespace,
                                  Map<String, Object> metadata) {
}
