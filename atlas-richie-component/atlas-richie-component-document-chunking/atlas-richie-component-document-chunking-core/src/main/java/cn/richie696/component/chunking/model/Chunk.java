package cn.richie696.component.chunking.model;

/**
 * 输入字符串的不可变切片单元；字符区间采用左闭右开（{@code [charStart, charEnd)}）。
 *
 * <p>本类是切片组件对外的最小数据契约，同步与流式两条路径都返回该类型。
 * {@link #charStart()}、{@link #charEnd()} 始终相对于调用 {@code ChunkingService.chunk} 时
 * 传入的原始字符串（或 {@code StreamingChunker} 视角下的连续输入流），因此调用方可以
 * 无歧义地将任意切片回映到自己的原文 / 解析产物。{@link #ordinal()} 仅是当前会话内的
 * 单调递增序号，业务字段（如文档 ID、版本号、页码）由编排层另行关联。</p>
 *
 * <p>本类不可变；任何 {@link #text()}、区间、序号的修改都应生成新的实例。</p>
 *
 * @param ordinal 当前切片在所属结果序列中的位置；从 0 起严格递增 1，由组件自身负责
 * @param text 切片文本；非空、已剥离前后空白；长度与 {@code charEnd - charStart} 相等
 * @param charStart 切片起始字符下标（左闭），相对原始输入字符串；满足 {@code 0 <= charStart < charEnd}
 * @param charEnd 切片结束字符下标（右开），相对原始输入字符串
 */
public record Chunk(int ordinal, String text, int charStart, int charEnd) {
    /**
     * 规范构造器：在 record 字段赋值前对所有不变量进行校验。
     *
     * <p>校验策略：序号必须非负；文本必须存在且至少含一个非空白字符；起止下标非负且
     * 满足 {@code charStart <= charEnd}。任何一项不满足立即抛出 {@link IllegalArgumentException}，
     * 避免下游静默消费损坏数据。</p>
     *
     * @throws IllegalArgumentException 当任一不变量不满足时抛出
     */
    public Chunk {
        if (ordinal < 0 || text == null || text.isBlank() || charStart < 0 || charEnd < charStart) {
            throw new IllegalArgumentException("非法 Chunk");
        }
    }
}
