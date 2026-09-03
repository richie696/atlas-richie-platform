package cn.richie696.component.vector.embeddings;

import org.springframework.ai.embedding.EmbeddingModel;

/** Business-owned immutable index/model binding. Implementations must fail closed, never choose a default. */
@FunctionalInterface
public interface IndexEmbeddingModelResolver {
    EmbeddingModel resolve(String indexName);
}
