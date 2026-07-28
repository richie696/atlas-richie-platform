package cn.richie696.component.chunking.strategy;

import cn.richie696.component.chunking.model.ChunkingRule;
import java.util.List;

/** 优先保持自然段完整的切片策略。 */
public final class ParagraphChunkingStrategy extends AbstractCharacterChunkingStrategy {
    private static final List<String> SEPARATORS = List.of("\n\n", "\n");
    ParagraphChunkingStrategy(ChunkingStrategySupport support) { super(support); }
    @Override public ChunkingRule.Strategy type() { return ChunkingRule.Strategy.PARAGRAPH; }
    @Override public int boundaryAtOrBefore(CharSequence text, ChunkingRule rule, int limit) {
        return lastBoundary(text, SEPARATORS, limit);
    }
}
