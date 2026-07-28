package cn.richie696.component.chunking;

import cn.richie696.component.chunking.model.ChunkingRule;

import java.util.Objects;

/**
 * 由自动装配配置创建每文档独占的流式切片会话。
 *
 * <p>本工厂是 core 对外暴露的“流式切片入口”，被 Spring WebFlux、parser 适配层、
 * 文档批处理等场景复用：调用方拿到 {@link StreamingChunkerFactory} 后，对每份新文档
 * 调一次 {@link #create(ChunkingRule)} 拿到独立 {@link StreamingChunker} 即可。
 * 工厂不持有文档状态，因此是线程安全的。</p>
 *
 * <p>依赖方向：仅依赖 {@link ChunkingService}，不持有 Spring / AI / parser 依赖。</p>
 */
public final class StreamingChunkerFactory {

    private final ChunkingService chunkingService;
    private final int maxPendingCharacters;

    /**
     * 构造器：注入底层 {@link ChunkingService} 与默认 pending 缓冲上限。
     *
     * <p>注意：{@code maxPendingCharacters} 是“工厂持有的默认值”，每次
     * {@link #create(ChunkingRule)} 还会再用 {@link Math#max(int, int)} 与新规则的
     * {@link ChunkingRule#maxCharacters()} 取较大值，避免传入的规则实际容量大于默认上限
     * 时 {@link StreamingChunker} 构造失败。</p>
     *
     * @param chunkingService 委托的同步切片器；必须非空
     * @param maxPendingCharacters 默认的 pending 缓冲字符上限；可以小于规则上限（会被覆盖）
     * @throws NullPointerException {@code chunkingService} 为 {@code null}
     */
    public StreamingChunkerFactory(ChunkingService chunkingService, int maxPendingCharacters) {
        this.chunkingService = Objects.requireNonNull(chunkingService, "chunkingService must not be null");
        this.maxPendingCharacters = maxPendingCharacters;
    }

    /**
     * 为单份文档创建专属 {@link StreamingChunker}。
     *
     * <p>{@code maxPendingCharacters} 取 {@code Math.max(factory.maxPendingCharacters, rule.maxCharacters())}，
     * 而不是直接使用工厂字段 —— 因为规则可能在调用方运行时被替换为大字符上限；不让新会话因
     * 工厂默认上限偏小而构造失败，能让“streaming 实际容量 &ge; 规则上限”这一不变量始终成立，
     * 也是 {@link StreamingChunker} 构造器对 {@code maxPendingCharacters >= maxCharacters}
     * 校验的提前保障。</p>
     *
     * @param rule 本次会话的规则快照；SEMANTIC 会被 {@link StreamingChunker} 拒绝
     * @return 独立的、未启动的流式切片会话；调用方持有并驱动其生命周期
     * @throws NullPointerException {@code rule} 为 {@code null}
     */
    public StreamingChunker create(ChunkingRule rule) {
        Objects.requireNonNull(rule, "rule must not be null");
        return new StreamingChunker(chunkingService, rule,
                Math.max(maxPendingCharacters, rule.maxCharacters()));
    }
}
