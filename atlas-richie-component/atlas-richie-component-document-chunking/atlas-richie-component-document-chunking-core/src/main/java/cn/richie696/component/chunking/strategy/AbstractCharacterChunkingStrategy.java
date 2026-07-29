package cn.richie696.component.chunking.strategy;

import cn.richie696.component.chunking.model.Chunk;
import cn.richie696.component.chunking.model.ChunkingResult;
import cn.richie696.component.chunking.model.ChunkingRule;
import cn.richie696.component.chunking.model.ChunkingSignal;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 以字符窗口切片的策略基类，统一处理 overlap、坐标和小尾段。
 */
abstract class AbstractCharacterChunkingStrategy implements StreamingChunkingStrategy {

    private final ChunkingStrategySupport support;

    AbstractCharacterChunkingStrategy(ChunkingStrategySupport support) {
        this.support = support;
    }

    @Override
    public final ChunkingResult chunk(String content, ChunkingRule rule) {
        List<Chunk> chunks = new ArrayList<>();
        int start = 0;
        boolean hardTruncated = false;
        while (start < content.length()) {
            int candidateEnd = Math.min(content.length(), start + rule.maxCharacters());
            int end = candidateEnd;
            if (candidateEnd < content.length()) {
                int boundary = boundaryAtOrBefore(content.subSequence(start, candidateEnd), rule, rule.maxCharacters());
                if (boundary > 0) {
                    end = start + boundary;
                }
                if (reportsMissingBoundary() && boundary >= candidateEnd - start) {
                    hardTruncated = true;
                }
            }
            support.append(chunks, content, start, end);
            if (end >= content.length()) {
                break;
            }
            start = Math.max(start + 1, end - rule.overlapCharacters());
        }
        List<Chunk> normalized = support.normalizeSmallTail(chunks, rule.maxCharacters(), content);
        Set<ChunkingSignal> signals = hardTruncated
                ? Set.of(ChunkingSignal.HARD_TRUNCATED, ChunkingSignal.SEPARATOR_NOT_FOUND)
                : Set.of();
        return support.result(content, rule, normalized, signals);
    }

    /**
     * 返回不超过 limit 的边界结束坐标；无自然边界时返回 limit。
     */
    public abstract int boundaryAtOrBefore(CharSequence text, ChunkingRule rule, int limit);

    /**
     * FIXED 天生硬切，不应把它标记为“未找到自然边界”。
     */
    protected boolean reportsMissingBoundary() {
        return true;
    }

    protected static int lastBoundary(CharSequence text, List<String> separators, int limit) {
        int capped = Math.clamp(limit, 1, text.length());
        if (capped == text.length()) {
            return capped;
        }
        for (String separator : separators) {
            if (separator == null || separator.isEmpty()) {
                continue;
            }
            int index = lastIndexOf(text, separator, capped - 1);
            if (index >= 0) {
                return index + separator.length();
            }
        }
        return capped;
    }

    private static int lastIndexOf(CharSequence text, String token, int fromIndex) {
        for (int index = Math.min(fromIndex, text.length() - token.length()); index >= 0; index--) {
            int offset = 0;
            while (offset < token.length() && text.charAt(index + offset) == token.charAt(offset)) {
                offset++;
            }
            if (offset == token.length()) {
                return index;
            }
        }
        return -1;
    }
}
