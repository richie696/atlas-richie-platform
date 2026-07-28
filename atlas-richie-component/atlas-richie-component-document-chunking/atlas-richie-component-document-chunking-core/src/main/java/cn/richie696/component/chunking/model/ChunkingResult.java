package cn.richie696.component.chunking.model;
import java.util.List;
import java.util.Set;

/**
 * 单次切片调用的不可变结果：有序 {@link Chunk} 列表 + 诊断信息。
 *
 * <p>{@link #chunks()} 中的元素满足：{@link Chunk#ordinal()} 从 0 起严格递增 1，
 * 区间与文本一致地映射回原始输入。任何 {@code null} 元素、空列表元素都会被规范构造器拒绝。
 * 该结果同时是同步 {@link cn.richie696.component.chunking.ChunkingService#chunk} 与流式
 * {@link cn.richie696.component.chunking.StreamingChunker} 路径的统一对外数据形状。</p>
 *
 * @param chunks 切片序列；按产生顺序，{@link Chunk#ordinal()} 从 0 起连续递增；空列表表示输入为空白或 {@code null}
 * @param diagnostics 诊断信息；记录是否触发硬截断、原始输入长度等审计字段；为 {@code null} 时自动回落为默认值
 */
public record ChunkingResult(List<Chunk> chunks, ChunkingDiagnostics diagnostics) {
    /**
     * 兼容旧调用方的便捷构造器：仅传入 {@link Chunk} 列表时，诊断字段使用默认值
     * {@code (hardTruncated=false, inputCharacters=0)}。
     *
     * <p>通常出现在只需要切片内容、不在意诊断信息的单元测试或轻量调用点。</p>
     *
     * @param chunks 切片序列；构造器会进一步调用 {@link List#copyOf} 固化
     */
    public ChunkingResult(List<Chunk> chunks) { this(chunks, new ChunkingDiagnostics(false, 0)); }
    /**
     * 规范构造器：把 {@link #chunks()} 固化为不可变副本，并在 {@link #diagnostics()} 为空时回落到默认值。
     *
     * <p>任何传入的 {@code null} 元素都会立即被 {@link List#copyOf} 拦截并抛
     * {@link NullPointerException}；诊断字段为 {@code null} 时使用 {@code (false, 0)}，
     * 避免下游出现空指针。</p>
     *
     * @throws NullPointerException 当 {@code chunks} 包含 {@code null} 元素时抛出
     */
    public ChunkingResult {
        chunks = List.copyOf(chunks);
        if (diagnostics == null) {
            diagnostics = new ChunkingDiagnostics(0, chunks.size(), null, null, Set.of());
        } else if (diagnostics.outputChunks() != chunks.size()) {
            diagnostics = new ChunkingDiagnostics(diagnostics.inputCharacters(), chunks.size(),
                    diagnostics.requestedStrategy(), diagnostics.appliedStrategy(), diagnostics.signals());
        }
    }
}
