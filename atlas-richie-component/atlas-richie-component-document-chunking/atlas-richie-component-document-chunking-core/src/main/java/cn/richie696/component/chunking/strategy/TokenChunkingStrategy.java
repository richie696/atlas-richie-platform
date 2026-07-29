package cn.richie696.component.chunking.strategy;

import cn.richie696.component.chunking.model.Chunk;
import cn.richie696.component.chunking.model.ChunkingResult;
import cn.richie696.component.chunking.model.ChunkingRule;
import cn.richie696.component.chunking.model.ChunkingSignal;
import cn.richie696.component.chunking.spi.TokenCounter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 以模型 token 预算而不是字符数确定边界的策略。
 */
public final class TokenChunkingStrategy implements StreamingChunkingStrategy {

    private final TokenCounter tokenCounter;
    private final ChunkingStrategySupport support;

    TokenChunkingStrategy(TokenCounter tokenCounter, ChunkingStrategySupport support) {
        this.tokenCounter = Objects.requireNonNull(tokenCounter, "tokenCounter must not be null");
        this.support = support;
    }

    @Override
    public ChunkingRule.Strategy type() {
        return ChunkingRule.Strategy.TOKEN;
    }

    @Override
    public ChunkingResult chunk(String content, ChunkingRule rule) {
        List<Chunk> chunks = new ArrayList<>();
        int start = 0;
        boolean hardTruncated = false;
        while (start < content.length()) {
            int end = maxEndWithinTokenLimit(content, start, rule.maxCharacters());
            if (end <= start) {
                end = Math.min(content.length(), start + 1);
                hardTruncated = true;
            }
            support.append(chunks, content, start, end);
            if (end >= content.length()) {
                break;
            }
            int overlap = Math.clamp(rule.overlapCharacters(), 0, end - start - 1);
            start = Math.max(start + 1, end - overlap);
        }
        List<Chunk> normalized = support.normalizeSmallTail(chunks, Integer.MAX_VALUE, content);
        Set<ChunkingSignal> signals = hardTruncated
                ? Set.of(ChunkingSignal.HARD_TRUNCATED, ChunkingSignal.TOKEN_LIMIT_FORCED)
                : Set.of();
        return support.result(content, rule, normalized, signals);
    }

    @Override
    public int boundaryAtOrBefore(CharSequence content, ChunkingRule rule, int limit) {
        int capped = Math.clamp(limit, 1, content.length());
        String window = content.subSequence(0, capped).toString();
        int end = maxEndWithinTokenLimit(window, 0, rule.maxCharacters());
        return end <= 0 ? Math.min(1, window.length()) : end;
    }

    private int maxEndWithinTokenLimit(String content, int start, int maxTokens) {
        int low = start + 1;
        int high = content.length();
        int best = start;
        while (low <= high) {
            int middle = low + (high - low) / 2;
            if (tokenCounter.count(content.substring(start, middle)) <= maxTokens) {
                best = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return best;
    }
}
