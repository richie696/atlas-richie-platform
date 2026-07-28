package cn.richie696.component.chunking.strategy;

import cn.richie696.component.chunking.SemanticChunkingService;
import cn.richie696.component.chunking.model.ChunkingResult;
import cn.richie696.component.chunking.model.ChunkingRule;

import java.util.Objects;

/**
 * 语义策略适配器。
 *
 * <p>模型调用不在这里发生；它只委托 core 的 {@link SemanticChunkingService}。具体模型适配
 * 通过 {@code SemanticBoundaryAdvisor} SPI 从可选模块注入，因此 core 不依赖 Spring AI 或任何
 * 模型 Provider。</p>
 */
public final class SemanticChunkingStrategy implements ChunkingStrategy {

    private final SemanticChunkingService delegate;

    public SemanticChunkingStrategy(SemanticChunkingService delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    @Override
    public ChunkingRule.Strategy type() {
        return ChunkingRule.Strategy.SEMANTIC;
    }

    @Override
    public ChunkingResult chunk(String content, ChunkingRule rule) {
        return delegate.chunk(content, rule);
    }

    @Override
    public boolean supportsStreaming() {
        return false;
    }
}
