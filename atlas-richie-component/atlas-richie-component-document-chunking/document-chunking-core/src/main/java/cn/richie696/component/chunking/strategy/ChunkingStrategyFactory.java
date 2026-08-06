package cn.richie696.component.chunking.strategy;

import cn.richie696.component.chunking.model.ChunkingRule;
import cn.richie696.component.chunking.spi.TokenCounter;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * 切片策略注册表与选择工厂。
 *
 * <p>工厂是策略选择的唯一位置。新增策略只需实现 {@link ChunkingStrategy} 并注册，
 * 不需要向 {@code ChunkingService}、边界选择器或流式编排器新增分支。</p>
 */
public final class ChunkingStrategyFactory {

    private final Map<ChunkingRule.Strategy, ChunkingStrategy> strategies =
            new EnumMap<>(ChunkingRule.Strategy.class);

    public ChunkingStrategyFactory(TokenCounter tokenCounter, int minChunkCharacters, int maxChunksPerDocument) {
        ChunkingStrategySupport support = new ChunkingStrategySupport(minChunkCharacters, maxChunksPerDocument);
        register(new FixedChunkingStrategy(support));
        register(new RecursiveChunkingStrategy(support));
        register(new TokenChunkingStrategy(tokenCounter, support));
        register(new ParagraphChunkingStrategy(support));
        register(new SentenceChunkingStrategy(support));
        register(new MarkdownChunkingStrategy(support));
        register(new HtmlChunkingStrategy(support));
        register(new PageChunkingStrategy(support));
    }

    /**
     * 注册一个策略；同一类型只能有一个实现，避免启动时出现不确定路由。
     */
    public void register(ChunkingStrategy strategy) {
        Objects.requireNonNull(strategy, "strategy must not be null");
        ChunkingStrategy previous = strategies.putIfAbsent(strategy.type(), strategy);
        if (previous != null) {
            throw new IllegalArgumentException("切片策略重复注册: " + strategy.type());
        }
    }

    /**
     * 按规则中的策略枚举选择实现。
     */
    public ChunkingStrategy select(ChunkingRule.Strategy type) {
        ChunkingStrategy strategy = strategies.get(Objects.requireNonNull(type, "strategy type must not be null"));
        if (strategy == null) {
            throw new UnsupportedOperationException("未注册切片策略: " + type
                    + "；SEMANTIC 策略需要提供 SemanticBoundaryAdvisor");
        }
        return strategy;
    }
}
