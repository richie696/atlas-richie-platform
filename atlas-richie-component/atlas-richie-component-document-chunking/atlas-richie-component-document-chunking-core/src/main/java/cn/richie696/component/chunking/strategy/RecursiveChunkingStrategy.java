package cn.richie696.component.chunking.strategy;

import cn.richie696.component.chunking.model.ChunkingRule;

/** 按调用方声明的分隔符优先级逐级降级的通用策略。 */
public final class RecursiveChunkingStrategy extends AbstractCharacterChunkingStrategy {
    RecursiveChunkingStrategy(ChunkingStrategySupport support) { super(support); }
    @Override public ChunkingRule.Strategy type() { return ChunkingRule.Strategy.RECURSIVE; }
    @Override public int boundaryAtOrBefore(CharSequence text, ChunkingRule rule, int limit) {
        return lastBoundary(text, rule.separators(), limit);
    }
}
