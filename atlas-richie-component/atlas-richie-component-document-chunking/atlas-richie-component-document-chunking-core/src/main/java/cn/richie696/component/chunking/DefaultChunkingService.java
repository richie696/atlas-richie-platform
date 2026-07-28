package cn.richie696.component.chunking;

import cn.richie696.component.chunking.model.ChunkingDiagnostics;
import cn.richie696.component.chunking.model.ChunkingResult;
import cn.richie696.component.chunking.model.ChunkingRule;
import cn.richie696.component.chunking.spi.SemanticBoundaryAdvisor;
import cn.richie696.component.chunking.spi.TokenCounter;
import cn.richie696.component.chunking.strategy.ChunkingStrategy;
import cn.richie696.component.chunking.strategy.ChunkingStrategyFactory;
import cn.richie696.component.chunking.strategy.SemanticChunkingStrategy;
import cn.richie696.component.chunking.strategy.StreamingChunkingStrategy;
import cn.richie696.component.chunking.strategy.StreamingStrategyResolver;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 默认同步入口：校验输入、选择策略并保持统一结果契约。
 *
 * <p>本类不再实现具体算法。八种确定性策略由 {@link ChunkingStrategyFactory} 在构造时注册；
 * 当提供 {@link SemanticBoundaryAdvisor} 时，第九种语义策略也会被注册。这样新增策略不会再
 * 反向膨胀这个入口类。</p>
 */
public final class DefaultChunkingService implements ChunkingService, StreamingStrategyResolver {

    private final ChunkingRule defaultRule;
    private final ChunkingStrategyFactory strategyFactory;

    /** 用于脱离 Spring 的快速调用：RECURSIVE、近似 token 计数器与默认保护阈值。 */
    public DefaultChunkingService() {
        this(ChunkingRule.recursiveDefaults(1_600, 160), approximateTokenCounter(), 80, 10_000, null);
    }

    /** 构造一个只包含八种确定性策略的服务。 */
    public DefaultChunkingService(ChunkingRule defaultRule, TokenCounter tokenCounter,
                                  int minChunkCharacters, int maxChunksPerDocument) {
        this(defaultRule, tokenCounter, minChunkCharacters, maxChunksPerDocument, null);
    }

    /**
     * 构造完整服务；advisor 存在时才注册 SEMANTIC 策略。
     *
     * <p>语义策略的 fallback 指向本服务本身，但 {@link SemanticChunkingService} 会先把规则
     * 派生为 RECURSIVE，因此不会发生语义策略递归调用。</p>
     */
    public DefaultChunkingService(ChunkingRule defaultRule, TokenCounter tokenCounter,
                                  int minChunkCharacters, int maxChunksPerDocument,
                                  SemanticBoundaryAdvisor semanticBoundaryAdvisor) {
        this.defaultRule = Objects.requireNonNull(defaultRule, "defaultRule must not be null");
        Objects.requireNonNull(tokenCounter, "tokenCounter must not be null");
        if (minChunkCharacters < 0 || maxChunksPerDocument <= 0) {
            throw new IllegalArgumentException("minChunkCharacters 和 maxChunksPerDocument 配置非法");
        }
        this.strategyFactory = new ChunkingStrategyFactory(tokenCounter, minChunkCharacters, maxChunksPerDocument);
        if (semanticBoundaryAdvisor != null) {
            strategyFactory.register(new SemanticChunkingStrategy(
                    new SemanticChunkingService(this, semanticBoundaryAdvisor)));
        }
    }

    @Override
    public ChunkingResult chunk(String content) {
        return chunk(content, defaultRule);
    }

    @Override
    public ChunkingResult chunk(String content, ChunkingRule rule) {
        Objects.requireNonNull(rule, "rule must not be null");
        if (content == null || content.isBlank()) {
            return new ChunkingResult(List.of(), new ChunkingDiagnostics(content == null ? 0 : content.length(), 0,
                    rule.strategy(), rule.strategy(), Set.of()));
        }
        ChunkingStrategy strategy = strategyFactory.select(rule.strategy());
        return strategy.chunk(content, rule);
    }

    @Override
    public StreamingChunkingStrategy streamingStrategy(ChunkingRule rule) {
        ChunkingStrategy strategy = strategyFactory.select(Objects.requireNonNull(rule, "rule must not be null").strategy());
        if (!(strategy instanceof StreamingChunkingStrategy streamingStrategy)) {
            throw new IllegalArgumentException("策略不支持流式切片: " + rule.strategy());
        }
        return streamingStrategy;
    }

    /**
     * 默认近似估算器：中文按字符、拉丁文本按约四字符一个 token。
     * 生产系统可通过构造器注入模型专用 {@link TokenCounter}。
     */
    public static TokenCounter approximateTokenCounter() {
        return text -> {
            if (text == null || text.isEmpty()) {
                return 0;
            }
            int tokens = 0;
            int latinRun = 0;
            for (int index = 0; index < text.length(); index++) {
                char character = text.charAt(index);
                if (Character.UnicodeScript.of(character) == Character.UnicodeScript.HAN) {
                    tokens++;
                    latinRun = 0;
                } else if (Character.isWhitespace(character) || isPunctuation(character)) {
                    tokens += latinRun == 0 ? 0 : Math.max(1, (latinRun + 3) / 4);
                    latinRun = 0;
                } else {
                    latinRun++;
                }
            }
            return tokens + (latinRun == 0 ? 0 : Math.max(1, (latinRun + 3) / 4));
        };
    }

    private static boolean isPunctuation(char character) {
        return switch (Character.getType(character)) {
            case Character.CONNECTOR_PUNCTUATION,
                    Character.DASH_PUNCTUATION,
                    Character.START_PUNCTUATION,
                    Character.END_PUNCTUATION,
                    Character.INITIAL_QUOTE_PUNCTUATION,
                    Character.FINAL_QUOTE_PUNCTUATION,
                    Character.OTHER_PUNCTUATION -> true;
            default -> false;
        };
    }
}
