package cn.richie696.component.chunking.config;

import cn.richie696.component.chunking.model.ChunkingRule;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.List;
import java.util.Locale;

/**
 * 文档切片组件配置；不包含模型或供应商配置。
 *
 * <p>绑定到 {@code platform.component.document-chunking.*} 前缀，与平台其他组件统一。
 * 仅描述确定性切片行为、资源上限与回退规则，绝不包含 API Key、模型名或 Tokenizer 配置。</p>
 *
 * <p>由 {@link ChunkingAutoConfiguration} 装配为 Spring Bean；{@link #defaultChunkingRule()}
 * 在装配时被调用一次，用于构造 {@code DefaultChunkingService}。</p>
 */
@Data
@Accessors(chain = true)
@ConfigurationProperties(prefix = "platform.component.document-chunking")
public class ChunkingProperties {

    /**
     * 是否启用本组件；{@code false} 时 {@link ChunkingAutoConfiguration} 不会注册任何 Bean。默认 {@code true}。
     */
    private boolean enabled = true;

    /**
     * 默认策略名；大小写不敏感，对应 {@link ChunkingRule.Strategy} 的字符串形式。
     * 不允许为 {@code "semantic"} —— 语义切片依赖完整上下文与 advisor，
     * 只能通过显式调用 {@link cn.richie696.component.chunking.SemanticChunkingService} 接入。
     */
    private String defaultRule = "recursive";

    /**
     * 单切片字符上限；必须 {@code > 0}，且严格大于 {@link #overlapCharacters}。
     * 同时决定流式切片 pending 缓冲区的下限。
     */
    private int maxCharacters = 1_600;

    /**
     * 相邻切片重叠字符数；{@code >= 0} 且 {@code < maxCharacters}。
     * 中文场景建议 {@code maxCharacters / 10} 左右；OCR / 弱结构文档可放大到 {@code 1/5}。
     */
    private int overlapCharacters = 160;

    /**
     * 小尾段合并阈值；当末段长度小于该值且与前一段合并后不超过 {@code maxCharacters} 时合并。
     * {@code 0} 表示关闭合并；典型值 60~120。
     */
    private int minChunkCharacters = 80;

    /**
     * 单文档允许产出的最大切片数；超出立即抛 {@link IllegalStateException} 防止内存爆炸。
     * 仅在同步切片路径生效；流式路径没有该上限，靠 pending 缓冲区控制。
     */
    private int maxChunksPerDocument = 10_000;

    /**
     * 流式切片专属配置；含缓冲区上限等运行时参数。
     */
    @NestedConfigurationProperty
    private Streaming streaming = new Streaming();

    /**
     * RECURSIVE 策略专属配置；分隔符列表优先级由前到后逐级降级。
     */
    @NestedConfigurationProperty
    private Recursive recursive = new Recursive();

    /**
     * 生成被自动装配 {@code ChunkingService} 使用的默认规则快照。
     *
     * <p>校验与归一化：(1) 长度 / overlap / chunk 上限等数值必须在合法区间；(2) 子配置
     * {@code streaming} / {@code recursive} 非空；(3) {@link #defaultRule} 经大写化后必须
     * 是合法 {@link ChunkingRule.Strategy}，{@code SEMANTIC} 显式拒绝；(4) 生成出来的
     * {@link ChunkingRule} 用 {@code "default-" + lowercase} 作 {@code ruleId}，
     * {@code "1"} 作 {@code version}，便于审计时一眼认出“默认规则快照”。</p>
     *
     * @return 用于 {@code DefaultChunkingService} 初始化的 {@link ChunkingRule} 快照
     * @throws IllegalArgumentException 数值或策略名非法时抛出
     */
    public ChunkingRule defaultChunkingRule() {
        if (maxCharacters <= 0 || overlapCharacters < 0 || overlapCharacters >= maxCharacters
                || minChunkCharacters < 0 || maxChunksPerDocument <= 0 || streaming == null
                || streaming.maxPendingCharacters <= 0 || recursive == null) {
            throw new IllegalArgumentException("document-chunking 配置非法");
        }
        ChunkingRule.Strategy strategy;
        try {
            strategy = ChunkingRule.Strategy.valueOf(defaultRule.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("不支持的 defaultRule: " + defaultRule, ex);
        }
        if (strategy == ChunkingRule.Strategy.SEMANTIC) {
            throw new IllegalArgumentException("默认规则不能是 SEMANTIC；请显式使用 SemanticChunkingService");
        }
        return new ChunkingRule("default-" + defaultRule.toLowerCase(Locale.ROOT), "1", strategy,
                maxCharacters, overlapCharacters, recursive.getSeparators());
    }

    /**
     * 流式切片专属参数集合。
     */
    @Data
    @Accessors(chain = true)
    public static class Streaming {
        /**
         * 单个文档会话最多保留的未确定尾部字符数。
         *
         * <p>实际生效值还会与 {@link ChunkingRule#maxCharacters()} 取较大值
         * （{@link cn.richie696.component.chunking.StreamingChunkerFactory#create}），
         * 避免规则变大后 streaming 容量反而不够。默认 8192，覆盖绝大多数 RAG 文档。</p>
         */
        private int maxPendingCharacters = 8_192;
    }

    /**
     * RECURSIVE 策略专属参数集合。
     */
    @Data
    @Accessors(chain = true)
    public static class Recursive {
        /**
         * RECURSIVE 策略使用的分隔符优先级列表；从前往后逐级降级。
         *
         * <p>默认按“段落 → 换行 → 中文句末 → 英文句末 → 空格”顺序，
         * 适配中英文混合长文档；调用方可按文档类型收紧 / 放宽。</p>
         */
        private List<String> separators = List.of("\n\n", "\n", "。", "！", "？", ". ", " ");
    }
}
