package cn.richie696.component.chunking.strategy;

import cn.richie696.component.chunking.model.ChunkingRule;

/** 能在增量输入中确定下一个完整边界的确定性切片策略。 */
public interface StreamingChunkingStrategy extends ChunkingStrategy {
    int boundaryAtOrBefore(CharSequence content, ChunkingRule rule, int limit);
}
