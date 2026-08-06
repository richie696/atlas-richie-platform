package cn.richie696.component.chunking.strategy;

import cn.richie696.component.chunking.model.ChunkingRule;

import java.util.List;

/**
 * 按中英文句末标点优先断开的切片策略。
 */
public final class SentenceChunkingStrategy extends AbstractCharacterChunkingStrategy {
    private static final List<String> SEPARATORS = List.of("。", "！", "？", ". ", "! ", "? ");

    SentenceChunkingStrategy(ChunkingStrategySupport support) {
        super(support);
    }

    @Override
    public ChunkingRule.Strategy type() {
        return ChunkingRule.Strategy.SENTENCE;
    }

    @Override
    public int boundaryAtOrBefore(CharSequence text, ChunkingRule rule, int limit) {
        return lastBoundary(text, SEPARATORS, limit);
    }
}
