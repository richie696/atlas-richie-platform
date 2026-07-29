package cn.richie696.component.chunking.strategy;

import cn.richie696.component.chunking.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 策略共享的 Chunk 构造、尾段归并和诊断支持。
 */
final class ChunkingStrategySupport {

    private final int minChunkCharacters;
    private final int maxChunksPerDocument;

    ChunkingStrategySupport(int minChunkCharacters, int maxChunksPerDocument) {
        this.minChunkCharacters = minChunkCharacters;
        this.maxChunksPerDocument = maxChunksPerDocument;
    }

    void append(List<Chunk> chunks, String content, int rawStart, int rawEnd) {
        int start = skipLeadingWhitespace(content, rawStart, rawEnd);
        int end = skipTrailingWhitespace(content, start, rawEnd);
        if (start == end) {
            return;
        }
        if (chunks.size() >= maxChunksPerDocument) {
            throw new IllegalStateException("单文档 Chunk 数超过上限: " + maxChunksPerDocument);
        }
        chunks.add(new Chunk(chunks.size(), content.substring(start, end), start, end));
    }

    List<Chunk> normalizeSmallTail(List<Chunk> chunks, int maximumCharacters, String content) {
        if (chunks.size() < 2 || minChunkCharacters == 0) {
            return List.copyOf(chunks);
        }
        Chunk tail = chunks.getLast();
        Chunk previous = chunks.get(chunks.size() - 2);
        if (tail.text().length() >= minChunkCharacters
                || previous.text().length() + 1 + tail.text().length() > maximumCharacters) {
            return List.copyOf(chunks);
        }
        List<Chunk> merged = new ArrayList<>(chunks.subList(0, chunks.size() - 2));
        int start = previous.charStart();
        int end = tail.charEnd();
        merged.add(new Chunk(merged.size(), content.substring(start, end), start, end));
        return List.copyOf(merged);
    }

    ChunkingResult result(String content, ChunkingRule rule, List<Chunk> chunks, Set<ChunkingSignal> signals) {
        return new ChunkingResult(chunks, new ChunkingDiagnostics(content.length(), chunks.size(),
                rule.strategy(), rule.strategy(), signals));
    }

    private static int skipLeadingWhitespace(String content, int start, int end) {
        while (start < end && Character.isWhitespace(content.charAt(start))) {
            start++;
        }
        return start;
    }

    private static int skipTrailingWhitespace(String content, int start, int end) {
        while (end > start && Character.isWhitespace(content.charAt(end - 1))) {
            end--;
        }
        return end;
    }
}
