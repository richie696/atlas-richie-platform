package cn.richie696.component.chunking.strategy;

import cn.richie696.component.chunking.model.ChunkingRule;

import java.util.List;

/**
 * 以 Markdown 标题、代码块和段落为优先边界的策略。
 */
public final class MarkdownChunkingStrategy extends AbstractCharacterChunkingStrategy {
    private static final List<String> SEPARATORS = List.of("\n# ", "\n## ", "\n### ", "\n```", "\n\n", "\n");

    MarkdownChunkingStrategy(ChunkingStrategySupport support) {
        super(support);
    }

    @Override
    public ChunkingRule.Strategy type() {
        return ChunkingRule.Strategy.MARKDOWN;
    }

    @Override
    public int boundaryAtOrBefore(CharSequence text, ChunkingRule rule, int limit) {
        return lastBoundary(text, SEPARATORS, limit);
    }
}
