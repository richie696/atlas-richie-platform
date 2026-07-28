package cn.richie696.component.chunking.config;

import cn.richie696.component.chunking.ChunkingService;
import cn.richie696.component.chunking.DefaultChunkingService;
import cn.richie696.component.chunking.StreamingChunkerFactory;
import cn.richie696.component.chunking.model.ChunkingRule;
import cn.richie696.component.chunking.spi.SemanticBoundaryAdvisor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文档切片组件的 Spring Boot 自动装配入口。
 *
 * <p>装配条件：</p>
 * <ul>
 *   <li>{@code @ConditionalOnProperty(... enabled = "true", matchIfMissing = true)} —
 *       除非显式置 {@code platform.component.document-chunking.enabled=false}，否则随 Spring Boot
 *       启动自动生效。</li>
 *   <li>{@code @EnableConfigurationProperties(ChunkingProperties.class)} —
 *       把 {@code platform.component.document-chunking.*} 绑定为 {@link ChunkingProperties} Bean。</li>
 *   <li>{@code @ConditionalOnMissingBean} — 每个对外 Bean 都允许业务方覆盖。</li>
 * </ul>
 *
 * <p>对外暴露的 Bean：</p>
 * <ul>
 *   <li>{@code ChunkingService} — 默认 {@link DefaultChunkingService}，使用近似 token 计数器。</li>
 *   <li>{@code StreamingChunkerFactory} — 每文档独占 {@link cn.richie696.component.chunking.StreamingChunker} 的工厂。</li>
 * </ul>
 *
 * <p>当应用显式提供 {@code SemanticBoundaryAdvisor}（例如由可选 Spring AI 适配器构造）时，
 * 默认服务会额外注册 SEMANTIC 策略；没有 advisor 时该策略不可选择，避免无模型配置却悄然
 * 降级为其他算法。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "platform.component.document-chunking", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ChunkingProperties.class)
public class ChunkingAutoConfiguration {
    /**
     * 默认同步切片器 Bean：当业务容器内尚未提供 {@code ChunkingService} 时生效。
     *
     * <p>使用 {@link DefaultChunkingService#approximateTokenCounter()} 作为内置
     * {@link cn.richie696.component.chunking.spi.TokenCounter}，足以满足本地与开发环境；
     * 生产环境建议业务方自行提供模型专用实现并替换该 Bean。</p>
     *
     * @param properties 已绑定的切片组件配置
     * @return 装配好的同步切片器
     */
    @Bean
    @ConditionalOnMissingBean
    public ChunkingService chunkingService(ChunkingProperties properties,
                                           ObjectProvider<SemanticBoundaryAdvisor> semanticBoundaryAdvisor) {
        return new DefaultChunkingService(properties.defaultChunkingRule(),
                DefaultChunkingService.approximateTokenCounter(), properties.getMinChunkCharacters(),
                properties.getMaxChunksPerDocument(), semanticBoundaryAdvisor.getIfAvailable());
    }

    /**
     * 流式切片工厂 Bean：当业务容器内尚未提供 {@code StreamingChunkerFactory} 时生效。
     *
     * <p>默认 pending 上限直接复用 {@code ChunkingProperties.streaming.maxPendingCharacters}；
     * 单次 {@link StreamingChunkerFactory#create(ChunkingRule)} 时还会与规则的
     * {@link ChunkingRule#maxCharacters()} 取较大值，避免规则变大后 streaming 容量不足。</p>
     *
     * @param chunkingService 依赖本类暴露的同步切片器
     * @param properties 已绑定的切片组件配置
     * @return 装配好的流式切片工厂
     */
    @Bean
    @ConditionalOnMissingBean
    public StreamingChunkerFactory streamingChunkerFactory(ChunkingService chunkingService,
                                                            ChunkingProperties properties) {
        return new StreamingChunkerFactory(chunkingService,
                properties.getStreaming().getMaxPendingCharacters());
    }
}
