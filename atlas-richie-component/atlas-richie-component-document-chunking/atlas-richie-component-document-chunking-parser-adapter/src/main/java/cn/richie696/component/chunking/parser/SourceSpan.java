package cn.richie696.component.chunking.parser;

import cn.richie696.component.parser.model.ParsedSection;

/**
 * 连续文档流中一个 Chunk 所覆盖的 parser section 区间，字符坐标相对于该 section 文本。
 * <p>
 * {@code SourceSpan} 是 Chunk 与上游 {@link ParsedSection} 之间的双向桥接：
 * <ul>
 *   <li>{@link #sectionIndex} 指向该 span 所属 {@link ParsedSection} 在文档级联式 {@code ReadResult}
 *       中的位置；</li>
 *   <li>{@link #section} 直接持有原始 section，下游检索阶段可以无拷贝地回到
 *       {@code ParsedSection.text()}；</li>
 *   <li>{@link #charStart} 与 {@link #charEnd} 限定 Chunk 在该 section 文本中的字符坐标区间。</li>
 * </ul>
 *
 * <p>坐标约定（重要）：{@code charStart} 与 {@code charEnd} 永远相对于 {@code section.text()}
 * 内部下标，{@code 0} 表示该 section 的第一个字符，{@code section.text().length()} 表示
 * 末尾之后的下标；当一个 Chunk 横跨多个 {@code ParsedSection} 时，由
 * {@link ParserChunkingAdapter} 在内部把它拆成多个 {@code SourceSpan}，
 * {@link ChunkedSection#sourceSpans()} 在 {@code ChunkedSection} 完成时必须给出完整列表。
 *
 * <p>不可变性：record 字段在构造时由 compact constructor 校验，下游可直接安全读取。
 *
 * @param sectionIndex 所属 {@link ParsedSection} 在文档级联式 {@code ReadResult} 中的顺序下标，
 *                     必须 {@code >= 0}。
 * @param section      所属 {@link ParsedSection}，不可为 {@code null}，由上游解析组件提供。
 * @param charStart    Chunk 在 {@code section.text()} 中的起始下标（包含），必须
 *                     {@code >= 0} 且 {@code < charEnd}。
 * @param charEnd      Chunk 在 {@code section.text()} 中的结束下标（不含），必须
 *                     {@code <= section.text().length()}。
 */
public record SourceSpan(int sectionIndex, ParsedSection section, int charStart, int charEnd) {

    /**
     * 校验区间不变量。
     *
     * @throws IllegalArgumentException 当任一字段不满足上述不变量时抛出，消息固定为
     *                                  {@code "非法来源区间"}。
     */
    public SourceSpan {
        // 顺序检查由 short-circuit 保证：先排除空指针，再校验坐标单调性，最后兜底越界；
        // 这四项不变量任意一条失败都视作 "非法来源区间"，提示调用方修正 section 区间换算逻辑。
        if (sectionIndex < 0 || section == null || charStart < 0 || charEnd < charStart
                || charEnd > section.text().length()) {
            throw new IllegalArgumentException("非法来源区间");
        }
    }
}
