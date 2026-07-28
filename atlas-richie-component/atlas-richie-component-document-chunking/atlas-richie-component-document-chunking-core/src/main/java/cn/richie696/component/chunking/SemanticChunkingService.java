package cn.richie696.component.chunking;

import cn.richie696.component.chunking.model.Chunk;
import cn.richie696.component.chunking.model.ChunkingDiagnostics;
import cn.richie696.component.chunking.model.ChunkingResult;
import cn.richie696.component.chunking.model.ChunkingRule;
import cn.richie696.component.chunking.model.ChunkingSignal;
import cn.richie696.component.chunking.spi.SemanticBoundaryAdvisor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 基于外部边界建议的批式语义切片协调器；不依赖任何模型 SDK。
 *
 * <p>语义边界依赖完整上下文，因此该类型刻意不实现 {@link ChunkingService}，也不能用于
 * {@link StreamingChunker}。每个语义段仍委托确定性 fallback 服务做最大长度、overlap 与
 * 字符位置控制 —— 这样既能让语义建议发挥“在哪里切”的判断，又能复用 core 的硬切兜底、
 * 小尾段合并与区间映射能力。</p>
 *
 * <p>编排流程（{@link #chunk(String, ChunkingRule)}）：</p>
 * <pre>
 *   content ──► advisor.boundaries(content) ──► 归一化（去空 / 去 0 与 len / 去重 / 升序）
 *                            │
 *                            ▼
 *                  逐对 (start, boundary) ──► fallback.chunk(content[start, boundary), RECURSIVE)
 *                            │
 *                            ▼
 *                  把局部 charStart/charEnd 平移到全局坐标 + 重新编号 ordinal
 * </pre>
 * <p>依赖方向：传入 {@link ChunkingService} 作 fallback（典型为 {@link DefaultChunkingService}）、
 * 传入 {@link SemanticBoundaryAdvisor} 拿语义边界。本类不持有 Spring / AI SDK 依赖。</p>
 */
public final class SemanticChunkingService {

    private final ChunkingService fallback;
    private final SemanticBoundaryAdvisor advisor;

    /**
     * 构造器：注入 fallback 与 advisor。
     *
     * @param fallback 接收“每段语义子串”并做确定性长度控制的同步切片器
     * @param advisor  提供原始语义边界的 SPI
     * @throws NullPointerException {@code fallback} 或 {@code advisor} 为 {@code null}
     */
    public SemanticChunkingService(ChunkingService fallback, SemanticBoundaryAdvisor advisor) {
        this.fallback = Objects.requireNonNull(fallback, "fallback must not be null");
        this.advisor = Objects.requireNonNull(advisor, "advisor must not be null");
    }

    /**
     * 批式入口：把完整内容交给 advisor，按其建议切段后每段交给 fallback。
     *
     * <p>关键决策：(1) advisor 返回空 / {@code null} 时直接退回 fallback 全量切片；
     * (2) advisor 返回的边界会被归一化为合法下标（{@code 0 < value < content.length()}）、
     * 去重、升序；(3) 末段固定延伸到 {@code content.length()}，避免丢尾；
     * (4) 每段的局部 chunk 用 {@code + start} 平移到全局坐标，并按整体顺序重新分配 ordinal。</p>
     *
     * @param content 原始输入；{@code null} 或空白返回空结果
     * @param rule 切片规则；{@link ChunkingRule.Strategy} 必须为 {@code SEMANTIC}
     * @return 不变结果对象；不触发硬切（语义建议本身就是有意义的边界）
     * @throws NullPointerException {@code rule} 为 {@code null}
     */
    public ChunkingResult chunk(String content, ChunkingRule rule) {
        return chunk(content, rule, SemanticChunkingOptions.defaults());
    }

    /**
     * 使用显式失败策略执行语义切片。默认 fail-fast，只有调用方明确选择时才允许确定性降级。
     */
    public ChunkingResult chunk(String content, ChunkingRule rule, SemanticChunkingOptions options) {
        Objects.requireNonNull(rule, "rule must not be null");
        Objects.requireNonNull(options, "options must not be null");
        if (rule.strategy() != ChunkingRule.Strategy.SEMANTIC) {
            throw new IllegalArgumentException("SemanticChunkingService 仅接受 SEMANTIC 规则");
        }
        if (content == null || content.isBlank()) {
            return new ChunkingResult(List.of(), new ChunkingDiagnostics(content == null ? 0 : content.length(), 0,
                    rule.strategy(), rule.strategy(), Set.of()));
        }

        List<Integer> rawBoundaries;
        try {
            rawBoundaries = advisor.boundaries(content);
        } catch (RuntimeException error) {
            return onAdvisorFailure(content, rule, options, error);
        }
        List<Integer> boundaries = normalizeBoundaries(rawBoundaries, content.length());
        if (boundaries.isEmpty()) {
            boolean invalidOnly = rawBoundaries != null && !rawBoundaries.isEmpty();
            return deterministicFallback(content, rule,
                    invalidOnly ? Set.of(ChunkingSignal.INVALID_SEMANTIC_BOUNDARY) : Set.of());
        }

        List<Chunk> chunks = new ArrayList<>();
        int start = 0;
        for (int boundary : boundaries) {
            appendSegment(chunks, content, start, boundary, rule);
            start = boundary;
        }
        appendSegment(chunks, content, start, content.length(), rule);
        return new ChunkingResult(List.copyOf(chunks), new ChunkingDiagnostics(content.length(), chunks.size(),
                rule.strategy(), rule.strategy(), Set.of()));
    }

    private ChunkingResult onAdvisorFailure(String content, ChunkingRule rule, SemanticChunkingOptions options,
                                            RuntimeException error) {
        if (options.failureMode() == SemanticFailureMode.FAIL_FAST) {
            if (error instanceof SemanticBoundaryException semanticError) {
                throw semanticError;
            }
            throw new SemanticBoundaryException("语义边界服务调用失败", error);
        }
        return deterministicFallback(content, rule, Set.of(ChunkingSignal.SEMANTIC_FALLBACK));
    }

    private ChunkingResult deterministicFallback(String content, ChunkingRule semanticRule,
                                                  Set<ChunkingSignal> additionalSignals) {
        ChunkingResult fallbackResult = fallback.chunk(content, deterministicRule(semanticRule));
        Set<ChunkingSignal> signals = new java.util.HashSet<>(fallbackResult.diagnostics().signals());
        signals.addAll(additionalSignals);
        return new ChunkingResult(fallbackResult.chunks(), new ChunkingDiagnostics(content.length(),
                fallbackResult.chunks().size(), semanticRule.strategy(), ChunkingRule.Strategy.RECURSIVE, signals));
    }

    /**
     * 归一化 advisor 返回的原始边界：(1) 过滤 {@code null}；(2) 保留
     * {@code 0 < value < contentLength} 的合法下标；(3) 去重；(4) 升序。
     *
     * @param rawBoundaries advisor 返回的原始列表；可为 {@code null}
     * @param contentLength 当前输入的字符数；用于合法下标校验
     * @return 归一化后的不可变列表；为空时调用方走 fallback 全量切片
     */
    private List<Integer> normalizeBoundaries(List<Integer> rawBoundaries, int contentLength) {
        if (rawBoundaries == null || rawBoundaries.isEmpty()) {
            return List.of();
        }
        return rawBoundaries.stream()
                .filter(Objects::nonNull)
                .filter(value -> value > 0 && value < contentLength)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * 把 {@code content[start, end)} 当作独立输入交给 fallback，并把局部 chunk 的字符区间
     * 平移到全局坐标、按整体顺序重新编号 ordinal。
     *
     * @param output 累积的全局 chunk 列表
     * @param content 原始全文
     * @param start 当前语义段起点（含）
     * @param end 当前语义段终点（不含）
     * @param semanticRule SEMANTIC 规则；用于派生 fallback 用的 RECURSIVE 规则
     */
    private void appendSegment(List<Chunk> output, String content, int start, int end, ChunkingRule semanticRule) {
        if (end <= start) {
            return;
        }
        ChunkingResult result = fallback.chunk(content.substring(start, end), deterministicRule(semanticRule));
        for (Chunk chunk : result.chunks()) {
            // 平移局部坐标到全局坐标；ordinal 改用 output.size() 保证跨段连续。
            output.add(new Chunk(output.size(), chunk.text(), chunk.charStart() + start, chunk.charEnd() + start));
        }
    }

    /**
     * 把 SEMANTIC 规则改写成 RECURSIVE 规则后传给 fallback —— SEMANTIC 自身不直接做
     * 长度控制，长度 / overlap 与分隔符都借用语义规则中的同名字段。
     *
     * @param semanticRule 原始 SEMANTIC 规则
     * @return strategy 改为 RECURSIVE 的派生规则
     */
    private ChunkingRule deterministicRule(ChunkingRule semanticRule) {
        return new ChunkingRule(semanticRule.ruleId(), semanticRule.version(), ChunkingRule.Strategy.RECURSIVE,
                semanticRule.maxCharacters(), semanticRule.overlapCharacters(), semanticRule.separators());
    }
}
