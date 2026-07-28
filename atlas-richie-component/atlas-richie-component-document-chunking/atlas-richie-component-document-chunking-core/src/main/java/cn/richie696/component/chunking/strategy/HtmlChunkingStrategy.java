package cn.richie696.component.chunking.strategy;

import cn.richie696.component.chunking.model.ChunkingRule;
import java.util.List;

/** 在上游完成 HTML 清洗后，按常见结构标签收敛边界的策略。 */
public final class HtmlChunkingStrategy extends AbstractCharacterChunkingStrategy {
    private static final List<String> SEPARATORS = List.of("</h1>", "</h2>", "</h3>", "</p>", "</li>", "</tr>", "<br>", "<br/>");
    HtmlChunkingStrategy(ChunkingStrategySupport support) { super(support); }
    @Override public ChunkingRule.Strategy type() { return ChunkingRule.Strategy.HTML; }
    @Override public int boundaryAtOrBefore(CharSequence text, ChunkingRule rule, int limit) {
        return lastBoundary(text, SEPARATORS, limit);
    }
}
