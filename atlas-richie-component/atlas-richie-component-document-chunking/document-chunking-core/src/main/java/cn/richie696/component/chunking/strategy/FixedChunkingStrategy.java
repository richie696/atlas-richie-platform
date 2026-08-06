package cn.richie696.component.chunking.strategy;

import cn.richie696.component.chunking.model.ChunkingRule;

/**
 * 固定字符窗口策略：没有自然边界诉求，稳定作为最后兜底。
 */
public final class FixedChunkingStrategy extends AbstractCharacterChunkingStrategy {
    FixedChunkingStrategy(ChunkingStrategySupport support) {
        super(support);
    }

    @Override
    public ChunkingRule.Strategy type() {
        return ChunkingRule.Strategy.FIXED;
    }

    @Override
    public int boundaryAtOrBefore(CharSequence text, ChunkingRule rule, int limit) {
        return Math.clamp(limit, 1, text.length());
    }

    @Override
    protected boolean reportsMissingBoundary() {
        return false;
    }
}
