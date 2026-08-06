package cn.richie696.component.chunking.spi;

/**
 * 调用方实现的模型专属 token 计数器 SPI；core 不绑定任何 AI SDK、不内置任何分词器。
 *
 * <p>典型实现由调用方把目标模型的官方 Tokenizer（如 GPT、BGE、本地 SentencePiece）
 * 包装为该接口，并在装配 {@link cn.richie696.component.chunking.ChunkingService} 时注入；
 * core 仅在 {@link cn.richie696.component.chunking.model.ChunkingRule.Strategy#TOKEN} 策略下按二分搜索反复调用本接口，
 * 因此实现必须满足“重复调用同一文本必须返回稳定结果”的契约，否则切片边界会随调用抖动。</p>
 *
 * <p>当未注入实现时，core 通过 {@link cn.richie696.component.chunking.DefaultChunkingService#approximateTokenCounter()}
 * 提供粗略的中英文近似估算，仅用于本地开发或无模型场景。</p>
 */
@FunctionalInterface
public interface TokenCounter {
    /**
     * 计算给定文本对应的 token 数。
     *
     * <p><b>调用时机：</b>{@link cn.richie696.component.chunking.model.ChunkingRule.Strategy#TOKEN} 切片时按区间反复调用以确定
     * “不超过 {@link cn.richie696.component.chunking.model.ChunkingRule#maxCharacters()} 个 token”的最大字符窗口，并以此作为
     * 切片长度上限；二分搜索期间同一前缀可能被多次统计。</p>
     *
     * <p><b>调用方契约（前置条件）：</b></p>
     * <ul>
     *   <li>同一输入多次调用必须返回相同结果；建议做内部缓存。</li>
     *   <li>对于 {@code null} 或空字符串应返回 {@code 0}，而非抛异常 —— 这能让切片器在
     *       边界探测时安全退化。</li>
     *   <li>不应持有可变状态或产生副作用；本接口可能被并发调用。</li>
     * </ul>
     *
     * @param text 待统计的文本；可能为 {@code null} 或空串
     * @return 该文本对应的 token 数；{@code null} / 空串应返回 {@code 0}
     */
    int count(String text);
}
