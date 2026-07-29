package cn.richie696.component.chunking;

import cn.richie696.component.chunking.model.Chunk;
import cn.richie696.component.chunking.model.ChunkingResult;
import cn.richie696.component.chunking.model.ChunkingRule;
import cn.richie696.component.chunking.strategy.StreamingChunkingStrategy;
import cn.richie696.component.chunking.strategy.StreamingStrategyResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 每份文档独占的增量切片会话。
 *
 * <p>已经确认边界的内容立即发出；缓冲区只保留尚未形成完整块的尾部和下一块所需的 overlap。
 * {@link Chunk#charStart()} 与 {@link Chunk#charEnd()} 始终相对于连续输入流，而非每次
 * {@code drain} 的局部字符串 —— 因此跨多次 {@link #accept(String)} 调用的 ordinal 与
 * 字符区间都是单调自洽的。</p>
 *
 * <p>会话状态机：</p>
 * <pre>
 *                ┌─────────────┐                           ┌────────┐
 *      accept ─► │   ACTIVE    │ ── finish() / abort() ──► │ CLOSED │
 *                └─────────────┘                           └────────┘
 * </pre>
 * <p>{@link #finish()} 幂等：第二次调用直接返回空列表。
 * {@link #abort()} 也会切换到 {@code CLOSED}，且丢弃未发出的尾部。
 * {@link ChunkingRule.Strategy#SEMANTIC} 在构造期被拒绝 —— 语义切片需要完整上下文，
 * 不能在流式会话中工作。</p>
 *
 * <p>依赖方向：复用 {@link ChunkingService} 做局部切片，并从同一策略工厂取得
 * {@code StreamingChunkingStrategy} 做边界选择。所属工厂：{@link StreamingChunkerFactory}。
 * 本类不持有 Spring / AI / parser 依赖。</p>
 */
public final class StreamingChunker {

    private final ChunkingService chunkingService;
    private final ChunkingRule rule;
    private final StreamingChunkingStrategy streamingStrategy;
    private final int maxPendingCharacters;
    /**
     * 累积中的待切片文本；不包含已发出部分，include 上一轮保留的 overlap 尾巴。
     */
    private final StringBuilder pending = new StringBuilder();

    /**
     * 即将分配给下一个 emit 的全局 ordinal；保证跨多次 drain 的连续性。
     */
    private int nextOrdinal;
    /**
     * 已消费到 pending 之外的字符总数；用于把局部坐标映射到原始输入的全局坐标。
     */
    private long pendingStartOffset;
    /**
     * 已经向调用方发出的最大字符结束位置，用于在 flush 时丢弃纯 overlap 尾巴。
     */
    private int lastEmittedEnd;
    /**
     * 会话状态：{@code true} 后 {@link #accept(String)} 立即抛异常。
     */
    private boolean finished;

    /**
     * 便捷构造器：用 {@code maxCharacters + overlapCharacters} 作为 {@code maxPendingCharacters}
     * 默认值，刚好容纳“一个最大 chunk + 其 overlap 尾巴”，适配绝大多数 RECURSIVE 场景。
     *
     * @param chunkingService 委托的同步切片器
     * @param rule            本次会话的规则快照
     * @throws NullPointerException     任一参数为 {@code null}
     * @throws IllegalArgumentException 规则为 SEMANTIC
     */
    public StreamingChunker(ChunkingService chunkingService, ChunkingRule rule) {
        this(chunkingService, rule, safePendingLimit(rule));
    }

    /**
     * 全量构造器：调用方可显式指定 pending 缓冲区的上限。
     *
     * <p>参数边界：</p>
     * <ul>
     *   <li>{@code chunkingService} / {@code rule} 必须非空。</li>
     *   <li>不支持流式的策略（当前为 SEMANTIC）在策略解析阶段抛异常。</li>
     *   <li>{@code maxPendingCharacters} 必须 {@code >= rule.maxCharacters()}；否则在 buffer
     *       累积阶段就已无法容纳一个完整 chunk，导致 {@link #accept(String)} 持续不产出，
     *       逻辑上死锁。</li>
     * </ul>
     *
     * @param chunkingService      委托的同步切片器
     * @param rule                 本次会话的规则快照
     * @param maxPendingCharacters 缓冲区字符上限；{@code >= maxCharacters}
     * @throws NullPointerException     {@code chunkingService} 或 {@code rule} 为 {@code null}
     * @throws IllegalArgumentException 策略不支持流式或 {@code maxPendingCharacters < maxCharacters}
     */
    public StreamingChunker(ChunkingService chunkingService, ChunkingRule rule, int maxPendingCharacters) {
        this.chunkingService = Objects.requireNonNull(chunkingService, "chunkingService must not be null");
        this.rule = Objects.requireNonNull(rule, "rule must not be null");
        if (!(chunkingService instanceof StreamingStrategyResolver resolver)) {
            throw new IllegalArgumentException("流式切片需要由策略工厂驱动的 ChunkingService");
        }
        this.streamingStrategy = resolver.streamingStrategy(rule);
        if (maxPendingCharacters < rule.maxCharacters()) {
            throw new IllegalArgumentException("maxPendingCharacters 必须至少容纳一个 Chunk");
        }
        this.maxPendingCharacters = maxPendingCharacters;
    }

    /**
     * 接收一个上游文本片段，并返回本轮已确定边界的 Chunk。
     *
     * <p>行为：(1) 调用 {@link #ensureOpen()}，已 {@code finished} 时直接抛异常；
     * (2) 空 / 空白片段短路返回；(3) 非首段插入换行分隔符，避免两段粘连；(4) 调用
     * {@link #drainCompletedChunks()} 立即发出所有已能形成完整 chunk 的内容。</p>
     *
     * @param section 上游传入的一段文本；可为 {@code null} 或空白
     * @return 本轮新发出的 chunk 列表；未达阈值时为空
     * @throws IllegalStateException 会话已结束时抛出
     */
    public List<Chunk> accept(String section) {
        ensureOpen();
        if (section == null || section.isBlank()) {
            return List.of();
        }
        if (!pending.isEmpty()) {
            pending.append('\n');
        }
        pending.append(section);
        return drainCompletedChunks();
    }

    /**
     * 刷出文档尾部；该方法幂等。
     *
     * <p>将 {@code pending} 剩余内容作为一段完整输入交给 {@link ChunkingService} 处理，
     * 把结果按全局坐标映射后发出，并清空缓冲区。即使多次调用也只在第一次产生输出。</p>
     *
     * @return 本次 flush 实际新发出的 chunk 列表；已结束时为空
     */
    public List<Chunk> finish() {
        if (finished) {
            return List.of();
        }
        finished = true;
        if (pending.isEmpty()) {
            return List.of();
        }
        List<Chunk> output = mapEmitted(chunkingService.chunk(pending.toString(), rule));
        pendingStartOffset += pending.length();
        pending.setLength(0);
        return List.copyOf(output);
    }

    /**
     * 取消当前文档，丢弃未发出的尾部。
     *
     * <p>同样会把 {@code pendingStartOffset} 推进到当前位置，保证下一个新会话即便复用同
     * 一个底层资源也不会看到上一个文档的尾巴。会话进入 {@code CLOSED} 态，后续
     * {@link #accept(String)} 抛 {@link IllegalStateException}。</p>
     */
    public void abort() {
        finished = true;
        pendingStartOffset += pending.length();
        pending.setLength(0);
    }

    /**
     * 反复从 {@code pending} 切出已能形成完整 chunk 的部分。
     *
     * <p>循环条件：{@code pending.length() >= rule.maxCharacters()} —— 至少能切出一个
     * chunk。每轮委托当前策略在 {@code [0, maxCharacters]} 预算内找最近的合法边界；
     * 找不到时由策略返回自身的确定性兜底边界。</p>
     *
     * <p>overlap-aware 的核心：每次只消费 {@code end - overlapCharacters} 个字符，
     * 把 overlap 尾巴留在 {@code pending}，下一轮 {@link #accept(String)} 的新内容会以
     * 这段尾巴为前缀拼接，确保相邻 chunk 真正按规则重叠，而不是“上一段末尾 + 新增内容”
     * 这种隐式 overlap。</p>
     *
     * @return 本轮新发出的 chunk 列表
     * @throws IllegalStateException 缓冲区超出 {@code maxPendingCharacters}
     */
    private List<Chunk> drainCompletedChunks() {
        List<Chunk> output = new ArrayList<>();
        while (pending.length() >= rule.maxCharacters()) {
            int end = streamingStrategy.boundaryAtOrBefore(pending, rule, rule.maxCharacters());
            if (end <= 0) {
                end = rule.maxCharacters();
            }
            // 当前 Chunk 必须包含 overlap 尾部；只在消费缓冲区时保留它，下一块才会以该尾部为前缀。
            // 这样相邻 Chunk 的交集严格等于配置的 overlap，而不是把尚未 emit 的文本误当成 overlap。
            output.addAll(mapEmitted(chunkingService.chunk(pending.substring(0, end), rule)));
            // consumed = end - overlap：把“本次 emit 的尾部 overlap”留给下一轮作为前缀；
            // 至少消费 1 字符以避免 overlap == end 时死循环。
            int consumed = Math.max(1, end - rule.overlapCharacters());
            pending.delete(0, consumed);
            pendingStartOffset += consumed;
        }
        if (pending.length() > maxPendingCharacters) {
            throw new IllegalStateException("流式切片缓冲区超过上限: " + maxPendingCharacters);
        }
        return List.copyOf(output);
    }

    /**
     * 把一次局部切片的 {@link ChunkingResult} 映射成“相对于连续输入流的全局 Chunk”。
     *
     * <p>三次关键动作：(1) 用 {@link #offset(int)} 把局部 {@code charStart/charEnd} 加上
     * {@code pendingStartOffset} 转全局；(2) 用 {@code lastEmittedEnd} 去重 —— 上一次
     * drain 已发出的尾部 chunk 在下一次局部切片里可能再次出现，但已经在上一轮 emit 过；
     * (3) 用本会话单调递增的 {@link #nextOrdinal} 替换局部 ordinal，保证跨多次 drain 连续。</p>
     *
     * @param result 本次局部切片结果
     * @return 去重 + 重新编号后的全局 chunk 列表
     */
    private List<Chunk> mapEmitted(ChunkingResult result) {
        List<Chunk> output = new ArrayList<>(result.chunks().size());
        for (Chunk chunk : result.chunks()) {
            int start = offset(chunk.charStart());
            int end = offset(chunk.charEnd());
            if (end <= lastEmittedEnd) {
                continue;
            }
            output.add(new Chunk(nextOrdinal++, chunk.text(), start, end));
            lastEmittedEnd = Math.max(lastEmittedEnd, end);
        }
        return output;
    }

    /**
     * 把局部字符下标加上 {@link #pendingStartOffset} 转成全局下标；溢出 {@link Integer#MAX_VALUE}
     * 时抛 {@link IllegalStateException} —— 这意味着单文档已经超过 2 GiB，几乎必然是误用。
     *
     * @param localOffset 局部切片器返回的字符下标
     * @return 全局字符下标
     */
    private int offset(int localOffset) {
        long value = pendingStartOffset + localOffset;
        if (value > Integer.MAX_VALUE) {
            throw new IllegalStateException("文档字符偏移超过 Chunk 模型可表达范围");
        }
        return (int) value;
    }

    /**
     * 状态守卫：{@code finished} 后任何 {@link #accept(String)} 必须立即失败，
     * 避免“上一个文档的尾巴被偷偷算入下一个文档”。
     */
    private void ensureOpen() {
        if (finished) {
            throw new IllegalStateException("流式切片会话已结束，不能继续接收内容");
        }
    }

    /**
     * 为“未指定 maxPendingCharacters”场景提供兜底值：一个最大 chunk 加上其 overlap 尾巴，
     * 刚好允许一次 drain 完成而不被截断。
     *
     * @param rule 切片规则
     * @return {@code rule.maxCharacters() + rule.overlapCharacters()}
     */
    private static int safePendingLimit(ChunkingRule rule) {
        Objects.requireNonNull(rule, "rule must not be null");
        return rule.maxCharacters() + rule.overlapCharacters();
    }
}
