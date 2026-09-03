package cn.richie696.component.vector.model;

import java.util.Map;
import java.util.Objects;

/**
 * 由业务侧已选定 Embedding 模型生成的向量记录。
 *
 * <p>该类型用于“模型由业务版本绑定、向量库只负责数据面”的场景。Provider 不得再次
 * 调用进程级默认 EmbeddingModel，否则会把不同模型的向量混入同一索引。</p>
 */
public record PrecomputedVectorRecord(
        String id,
        String indexName,
        String content,
        Map<String, Object> metadata,
        float[] vector) {

    public PrecomputedVectorRecord {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (indexName == null || indexName.isBlank()) {
            throw new IllegalArgumentException("indexName must not be blank");
        }
        Objects.requireNonNull(vector, "vector must not be null");
        if (vector.length == 0) {
            throw new IllegalArgumentException("vector must not be empty");
        }
        content = content == null ? "" : content;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        vector = vector.clone();
    }

    @Override
    public float[] vector() {
        return vector.clone();
    }
}
