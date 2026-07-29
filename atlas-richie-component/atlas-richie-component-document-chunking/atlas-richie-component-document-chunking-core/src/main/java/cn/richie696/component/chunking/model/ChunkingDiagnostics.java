package cn.richie696.component.chunking.model;

import java.util.Objects;
import java.util.Set;

/**
 * 单次切片运行的算法级诊断；不包含文档标识、模型名和耗时等编排层观测数据。
 */
public record ChunkingDiagnostics(
        int inputCharacters,
        int outputChunks,
        ChunkingRule.Strategy requestedStrategy,
        ChunkingRule.Strategy appliedStrategy,
        Set<ChunkingSignal> signals
) {

    public ChunkingDiagnostics {
        if (inputCharacters < 0 || outputChunks < 0) {
            throw new IllegalArgumentException("inputCharacters 和 outputChunks 不能为负数");
        }
        signals = signals == null ? Set.of() : Set.copyOf(signals);
    }

    /** 保留原有简洁构造形式，供轻量调用方与历史调用点使用。 */
    public ChunkingDiagnostics( boolean hardTruncated, int inputCharacters){
        this(inputCharacters, 0, null, null,
                hardTruncated ? Set.of(ChunkingSignal.HARD_TRUNCATED) : Set.of());
    }

    public boolean hardTruncated () {
        return signals.contains(ChunkingSignal.HARD_TRUNCATED);
    }

    public boolean hasSignal (ChunkingSignal signal){
        return signals.contains(Objects.requireNonNull(signal, "signal must not be null"));
    }
}
