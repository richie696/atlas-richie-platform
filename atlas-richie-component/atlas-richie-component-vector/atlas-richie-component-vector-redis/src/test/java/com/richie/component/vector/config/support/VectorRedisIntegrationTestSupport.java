package com.richie.component.vector.config.support;

import com.richie.testing.redis.GenericRedisIntegrationTestSupport;
import com.richie.testing.redis.RedisIntegrationTestAccess;
import com.richie.testing.spring.PropertyContributor;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

public final class VectorRedisIntegrationTestSupport implements RedisIntegrationTestAccess {

    private static final GenericRedisIntegrationTestSupport DELEGATE = GenericRedisIntegrationTestSupport.create(
            DockerImageName.parse("redis/redis-stack-server:7.4.0-v1"),
            15,
            "Redis 集成测试需要 Docker。参见 atlas-richie-testing-support/README.md",
            VectorRedisIntegrationTestSupport::appendComponentProperties,
            "VECTOR");

    private VectorRedisIntegrationTestSupport() {
    }

    private static final VectorRedisIntegrationTestSupport INSTANCE = new VectorRedisIntegrationTestSupport();

    public static VectorRedisIntegrationTestSupport getInstance() {
        return INSTANCE;
    }

        /** JUnit { @EnabledIf} 入口（不可与实例 { #isEnabled()} 同名）。 */
    public static boolean integrationTestsEnabled() {
        return getInstance().isEnabled();
    }

    @Override
    public boolean isEnabled() {
        return DELEGATE.isEnabled();
    }

    @Override
    public boolean isExternal() {
        return DELEGATE.isExternal();
    }

    @Override
    public void appendPropertyPairs(List<String> pairs) {
        DELEGATE.appendPropertyPairs(pairs);
    }

    private static void appendComponentProperties(List<String> pairs) {
        // Spring AI 的 openai/ollama/miniMax 三个 EmbeddingModel AutoConfiguration 同 classpath 时
        // 会与本项目 AiModelAutoConfiguration 提供的 aiEmbeddingModel 一起创建多个 EmbeddingModel bean,
        // RedisVectorStoreAutoConfiguration / RedisVectorServiceImpl 无法选出唯一 bean。
        // 通过 spring.autoconfigure.exclude 字符串属性排除,无需在测试编译期引入对应模块。
        // 注意:atlas-richie-component-ai 的 AiModelAutoConfiguration 在测试环境默认不创建 aiEmbeddingModel
        // (要求 config-initialization-enabled=true 且 models 非空),所以 VectorIntegrationTestConfiguration
        // 还会显式提供一个名为 aiEmbeddingModel 的 stub bean,匹配 RedisVectorServiceImpl 的 @Qualifier。
        pairs.add("spring.autoconfigure.exclude="
                // 同 classpath 多个 EmbeddingModel starter 互相冲突,只保留 IT 显式提供的 aiEmbeddingModel stub。
                + "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration,"
                + "org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingAutoConfiguration,"
                + "org.springframework.ai.model.minimax.autoconfigure.MiniMaxEmbeddingAutoConfiguration,"
                // 全部 vendor Chat/Image/Audio/Moderation/Api starter 测试环境无 API key 且部分缺三方依赖
                // (okhttp3 等),factory method 实例化即失败污染上下文。框架级 observation/chat.memory/tool
                // 不依赖远端,保留以提供 micrometer/observation 支持。业务侧 ChatClient 用例由
                // atlas-richie-component-ai 自身 IT 覆盖。
                + "org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration,"
                + "org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration,"
                + "org.springframework.ai.model.minimax.autoconfigure.MiniMaxChatAutoConfiguration,"
                + "org.springframework.ai.model.moonshot.autoconfigure.MoonshotChatAutoConfiguration,"
                + "org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration,"
                + "org.springframework.ai.model.ollama.autoconfigure.OllamaApiAutoConfiguration,"
                + "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration,"
                + "org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration,"
                + "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration,"
                + "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration,"
                + "org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration,"
                + "org.springframework.ai.model.zhipuai.autoconfigure.ZhiPuAiChatAutoConfiguration,"
                + "org.springframework.ai.model.zhipuai.autoconfigure.ZhiPuAiEmbeddingAutoConfiguration,"
                + "org.springframework.ai.model.zhipuai.autoconfigure.ZhiPuAiImageAutoConfiguration");
        // Spring AI RedisVectorStoreAutoConfiguration 强制使用 JedisConnectionFactory,而 atlas-richie-component-cache
        // 默认配置 spring-boot-starter-data-redis(Lettuce),不显式设置 client-type=jedis 会导致注入失败。
        // 与 RedisVectorAutoConfiguration 的提示一致:"请确保未启用 Lettuce 定制,或显式配置 client-type=jedis"。
        pairs.add("spring.data.redis.client-type=jedis");
    }
}
