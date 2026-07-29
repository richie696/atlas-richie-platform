package cn.richie696.component.chunking.strategy;

import cn.richie696.component.chunking.model.ChunkingRule;

import java.util.List;

/**
 * 按页分隔符优先、空行兜底的策略。
 */
public final class PageChunkingStrategy extends AbstractCharacterChunkingStrategy {
    private static final List<String> SEPARATORS = List.of("\f", "\n\n");

    PageChunkingStrategy(ChunkingStrategySupport support) {
        super(support);
    }

    @Override
    public ChunkingRule.Strategy type() {
        return ChunkingRule.Strategy.PAGE;
    }

    @Override
    public int boundaryAtOrBefore(CharSequence text, ChunkingRule rule, int limit) {
        return lastBoundary(text, SEPARATORS, limit);
    }
}
