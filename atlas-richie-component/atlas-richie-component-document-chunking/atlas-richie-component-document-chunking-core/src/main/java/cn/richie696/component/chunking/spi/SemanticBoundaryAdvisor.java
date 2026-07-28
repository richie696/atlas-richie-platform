package cn.richie696.component.chunking.spi;

import cn.richie696.component.chunking.model.ChunkingRule;

import java.util.List;

/**
 * 语义边界建议 SPI；实现由调用方持有的任意模型 / 算法提供，core 不绑定具体 SDK。
 *
 * <p>典型实现包括：基于 LLM 的结构化输出、按主题模型打分阈值切分、基于规则的
 * {@code HeadingBreakAdvisor} 等。core 仅消费“已经产生的字符边界列表”，负责把
 * 这些建议归一化为有序、去重、合法的下标序列后作为 {@link cn.richie696.component.chunking.SemanticChunkingService}
 * 的段切输入。</p>
 */
@FunctionalInterface
public interface SemanticBoundaryAdvisor {
    /**
     * 返回内容中“应当在此切分”的字符下标序列。
     *
     * <p><b>调用时机：</b>仅在 {@link cn.richie696.component.chunking.model.ChunkingRule.Strategy#SEMANTIC} 策略下，由
     * {@link cn.richie696.component.chunking.SemanticChunkingService#chunk(String, ChunkingRule)}
     * 在批式入口一次性同步调用；不会在 {@link cn.richie696.component.chunking.StreamingChunker}
     * 中被调用 —— 语义边界依赖完整上下文。</p>
     *
* <p><b>调用方契约（前置条件）：</b></p>
 * <ul>
 *   <li>返回值按 0-based、相对于 {@code content} 的字符下标（左闭右开区间的起始位置），
 *       表示“下标处之后应开始新段”。</li>
 *   <li>下标必须满足 {@code 0 < value < content.length()}；等于 0、等于
 *       {@code content.length()}、{@code null} 元素都会被
 *       {@link cn.richie696.component.chunking.SemanticChunkingService} 内部过滤。</li>
 *   <li>返回 {@code null} 或空列表时，core 自动回退到确定性 RECURSIVE 策略；
 *       因此建议器可以安全地“拿不准就交还”。</li>
 *   <li>建议器自身不必排序或去重 —— 归一化逻辑由 core 完成。</li>
 * </ul>
     *
     * @param content 完整输入文本
     * @return 期望切分位置的下标集合；返回 {@code null} / 空集合代表“不建议切”，core 自动回退
     */
    List<Integer> boundaries(String content);
}
