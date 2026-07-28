package cn.richie696.component.chunking.parser;

import cn.richie696.component.chunking.model.ChunkingResult;
import cn.richie696.component.parser.model.ParsedSection;

import java.util.List;

/**
 * 保留 parser 来源上下文的切片结果，不属于纯字符串 core。
 * <p>
 * 一个 {@code ChunkedSection} 实例对应上游解析组件 {@link ParsedSection} 在某个 {@link ChunkingRule}
 * 下的一次切片输出，承担两件事：
 * <ul>
 *   <li>把 chunk 抽象的纯字符串切片结果（{@link ChunkingResult}）继续向上游溯源，回到具体的
 *       {@link ParsedSection}；</li>
 *   <li>把每个 Chunk 落到原文区间的那一部分位置以 {@link SourceSpan} 列表形式保存，方便
 *       检索阶段回到原始 {@code ParsedSection.text()} 还原上下文。</li>
 * </ul>
 *
 * <p>本类与 vector 组件的契约边界：此适配器只产出 {@code ChunkedSection}，并不组装
 * {@code VectorRecord}；{@code VectorRecord} 的字段映射由
 * {@code atlas-richie-component-vector-chunk-adapter} 模块承担。
 *
 * <p>不变性：每次切片都通过构造器分配全新的 record 实例，不持有跨请求的缓存或可变状态，
 * 单实例可被任意下游消费者自由持有或转发。
 *
 * @param sectionIndex 切片对应的上游 {@link ParsedSection} 顺序索引（从 0 起，与
 *                     {@code ReadResult.sections()} 下标一致）。
 * @param fileName     来源文件名（来自 {@code ReadEvent.Section.fileName()}），可能为
 *                     {@code null}，表示源文档未携带可识别文件名时按"未知源"处理。
 * @param source       被切片的原始 {@link ParsedSection}，不会为 {@code null}。
 * @param result       本次切片产出的 {@link ChunkingResult}，由 {@link ChunkingService} 生成。
 * @param sourceSpans  描述本 Chunk 实际覆盖到 {@code source.text()} 中哪些字符区间；可能为
 *                     空列表（表示 Chunk 在该 section 上无落点，例如批式入口的某些切片）。
 */
public record ChunkedSection(int sectionIndex, String fileName, ParsedSection source,
                             ChunkingResult result, List<SourceSpan> sourceSpans) {

    public ChunkedSection {
        // sourceSpans 来自下游组装，防御性拷贝以切断外部可变集合引用；
        // null 收敛为不可变空列表，避免下游 NPE 并把 "无区间" 与 "缺失区间" 等价看待。
        sourceSpans = sourceSpans == null ? List.of() : List.copyOf(sourceSpans);
    }

    /**
     * 兼容单 section 批式映射的便利构造器；流式跨段 Chunk 请读取 {@link #sourceSpans()}。
     * <p>
     * 等价于用"该 Chunk 完全落在本 section 内、覆盖区间为整段文本"的单一
     * {@link SourceSpan} 调用全参构造器：
     * <pre>{@code
     *     List.of(new SourceSpan(sectionIndex, source, 0, source.text().length()))
     * }</pre>
     * 当 {@code source} 为 {@code null} 时不再构造任何 span，直接以空列表兜底，
     * 保持与下游 {@link #sourceSpans()} 不可空约定一致。
     *
     * @param sectionIndex 见 {@link #sectionIndex()}。
     * @param fileName     见 {@link #fileName()}，可空。
     * @param source       见 {@link #source()}，可空。
     * @param result       见 {@link #result()}。
     */
    public ChunkedSection(int sectionIndex, String fileName, ParsedSection source, ChunkingResult result) {
        this(sectionIndex, fileName, source, result,
                source == null ? List.of() : List.of(new SourceSpan(sectionIndex, source, 0, source.text().length())));
    }
}
