/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.ai.config;


import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文件存储服务配置信息
 *
 * @author richie696
 * @version 1.0
 * @since 2023-09-05 10:56:09
 */
@Data
@ConfigurationProperties(prefix = "platform.component.ai")
public class AiModelProperties {

    /**
     * 默认构造函数
     * 初始化AI模型配置属性对象
     */
    public AiModelProperties() {
    }

    /**
     * 是否启用配置文件初始化
     * true: 启动时按 application.yml 初始化模型
     * false: 仅允许运行时通过 initializeModels 动态初始化
     */
    private boolean configInitializationEnabled = true;

    /**
     * 模型路由与降级配置
     */
    private RoutingConfig routing = new RoutingConfig();

    /**
     * 调用韧性配置（熔断等）
     */
    private ResilienceConfig resilience = new ResilienceConfig();

    /**
     * 健康检查配置
     */
    private HealthCheckConfig healthCheck = new HealthCheckConfig();

    private Map<String, AiModel> models;

    /**
     * AI模型定义
     *
     * @author richie696
     * @version 1.0
     * @since 2025-07-09 13:48:32
     */
    @Data
    public static class AiModel {

        /**
         * 默认构造函数
         * 初始化AI模型配置对象
         */
        public AiModel() {
        }

        /**
         * 模型提供商
         */
        private AiProviderType provider;

        /**
         * API密钥
         */
        private String apiKey;

        /**
         * 模型地址
         */
        private String baseUrl;

        /**
         * 模型参数
         */
        private AiModelOptions options;

    }

    /**
     * AI模型提供商枚举
     */
    public enum AiProviderType {
        OPENAI,
        DEEPSEEK,
        ZHIPUAI,
        ANTHROPIC,
        OLLAMA,
        MINIMAX,
        MOONSHOT
    }

    /**
     * AI模型参数
     *
     * @author richie696
     * @version 1.0
     * @since 2025-07-09 13:48:14
     */
    @Data
    @Accessors(chain = true)
    public static class AiModelOptions {

        /**
         * 默认构造函数
         * 初始化AI模型参数配置对象
         */
        public AiModelOptions() {
        }

        /**
         * 基础参数：模型名称（可选参数）
         */
        private String model;

        /**
         * 基础参数：限制生成的最大令牌数（可选参数,不提供则无限制）
         */
        private Integer maxTokens;

        /**
         * 基础参数：控制输出的随机性
         * <pre>
         *   低温设置 (0.1-0.3): 产生更确定、可预测的输出，适合事实性任务
         *   中温设置 (0.4-0.7): 平衡创造性和一致性，适合一般对话
         *   高温设置 (0.8-1.0): 产生更多样、创造性的输出，适合创意写作
         *
         *   【注意：如果当前模型是Deepseek，则该值必须在0.0到2.0之间。】
         *   低温区间 (0.0 - 0.3)
         *     极度确定性：生成非常可预测、一致性强的输出
         *     适用场景：事实查询、代码生成、逻辑推理等需要高精确度的任务
         *     输出特点：几乎没有创造性变化，每次生成的结果非常相似
         *   中低温区间 (0.4 - 0.7)
         *     适度确定性：生成相对稳定但有一定变化的输出
         *     适用场景：一般问答、技术文档生成、商业内容写作
         *     输出特点：保持内容可靠性的同时有一定的表达变化
         *   中温区间 (0.8 - 1.2)
         *     平衡区间：创造性和确定性较为平衡
         *     适用场景：一般对话、内容概述、多样化的解释
         *     输出特点：有明显的表达多样性，但仍然保持主题连贯
         *   中高温区间 (1.3 - 1.6)
         *     增强创造性：生成更多样、更具探索性的内容
         *     适用场景：创意写作、头脑风暴、多角度探讨
         *     输出特点：表达更加自由，可能出现意外但有趣的联想
         *   高温区间 (1.7 - 2.0)
         *     极高随机性：生成高度创造性、不可预测的输出
         *     适用场景：创意故事创作、诗歌生成、艺术性文本
         *     输出特点：内容变化极大，可能偏离主题，但创造性最强
         *   与其他参数的关系
         *     与 topP 参数配合：一般建议只调整 temperature 或 topP 其中之一，而不是同时调整两者
         *     与 maxTokens 关系：高温度设置可能导致模型生成更长、更发散的内容，可能需要适当调整 maxTokens
         *   实际应用建议
         *     技术文档/代码生成：使用 0.1-0.3 的低温度
         *     一般问答/商业内容：使用 0.4-0.7 的温度
         *     创意内容/多样化回答：使用 0.8-1.5 的温度
         *     艺术创作/极度创新内容：使用 1.6-2.0 的高温度
         * </pre>
         */
        private Double temperature;
        /**
         * 基础参数：控制输出的多样性
         * <pre>
         *   核采样（Nucleus Sampling）是一种替代温度采样的方法，模型只考虑概率质量达到top_p的token结果。
         *   例如，0.1表示只考虑概率质量最高的10%的token。
         *
         *   使用建议：
         *   1. 通常建议只调整temperature或top_p中的一个，而不是同时调整两者
         *   2. 较低的值（如0.1-0.3）会产生更确定、更聚焦的输出
         *   3. 较高的值（如0.7-1.0）会产生更多样化、更有创造性的输出
         *
         *   与temperature的关系：
         *   - 两者都是控制输出随机性的参数
         *   - temperature通过调整概率分布的形状来控制随机性
         *   - top_p通过直接截断概率分布来控制随机性
         *
         *   实际应用建议：
         *   - 需要精确、可预测的输出时，使用较低的值（0.1-0.3）
         *   - 需要平衡创造性和一致性时，使用中等值（0.4-0.7）
         *   - 需要更多样化、创造性的输出时，使用较高值（0.8-1.0）
         * </pre>
         */
        private Double topP;

        /**
         * Anthropic专属参数：控制输出的多样性
         * <pre>
         * 限制每一步只考虑概率最高的 K 个选项
         * 较低的值（如 10）使响应更加聚焦
         * 较高的值（如 50）允许更多的变化
         * </pre>
         */
        private Integer topK;

        /**
         * 基础参数：频率惩罚系数
         * <pre>
         *   取值范围：-2.0 到 2.0
         *
         *   作用机制：
         *   1. 正值会根据token在文本中已出现的频率进行惩罚
         *   2. 负值会鼓励重复使用已出现的token
         *
         *   使用场景：
         *   - 当需要避免重复内容时，使用正值（0.1-2.0）
         *   - 当需要强调某些概念或保持一致性时，使用负值（-2.0-0）
         *
         *   实际应用建议：
         *   - 技术文档生成：使用较小的正值（0.1-0.5）避免术语重复
         *   - 创意写作：可以使用负值（-0.5-0）增加主题连贯性
         *   - 一般对话：建议使用0，保持自然表达
         *
         *   注意事项：
         *   - 过高的正值可能导致输出过于分散
         *   - 过低的负值可能导致输出过于重复
         *   - 建议与temperature和presencePenalty配合使用
         * </pre>
         */
        private Double frequencyPenalty;

        /**
         * 基础参数：存在惩罚系数
         * <pre>
         *   取值范围：-2.0 到 2.0
         *
         *   作用机制：
         *   1. 正值会惩罚已经出现过的token，鼓励模型讨论新话题
         *   2. 负值会鼓励重复使用已出现的token，保持话题聚焦
         *
         *   使用场景：
         *   - 需要探索新话题时，使用正值（0.1-2.0）
         *   - 需要深入讨论特定话题时，使用负值（-2.0-0）
         *
         *   实际应用建议：
         *   - 头脑风暴：使用正值（0.5-2.0）鼓励发散思维
         *   - 深度分析：使用负值（-1.0-0）保持话题聚焦
         *   - 一般对话：建议使用0，保持自然对话流
         *
         *   注意事项：
         *   - 与frequencyPenalty的区别：
         *     * frequencyPenalty关注token出现的频率
         *     * presencePenalty关注token是否出现过
         *   - 建议与temperature和frequencyPenalty配合使用
         * </pre>
         */
        private Double presencePenalty;

        /**
         * 基础参数：停止词列表
         * <pre>
         *   功能说明：
         *   1. 可以设置最多4个停止词
         *   2. 当模型生成遇到这些词时，将停止继续生成
         *
         *   使用场景：
         *   - 控制输出长度：设置特定的结束标记
         *   - 避免特定内容：设置不希望出现的内容
         *   - 格式化输出：确保输出符合特定格式
         *
         *   实际应用建议：
         *   - 对话系统：设置对话结束标记
         *   - 内容生成：设置段落结束标记
         *   - 代码生成：设置代码块结束标记
         *
         *   注意事项：
         *   - 停止词要选择明确、不会误触发的词
         *   - 过多的停止词可能影响生成质量
         *   - 建议根据具体场景谨慎选择停止词
         * </pre>
         */
        private List<String> stop;

        /**
         * 基础参数：是否返回输出token的对数概率
         * <pre>
         *   功能说明：
         *   1. 控制是否返回每个输出token的对数概率
         *   2. 当设置为true时，返回每个token的概率信息
         *
         *   使用场景：
         *   - 模型评估：分析模型输出的确定性
         *   - 质量控制：评估生成内容的质量
         *   - 调试分析：了解模型的决策过程
         *
         *   实际应用建议：
         *   - 开发测试：开启此选项进行模型调优
         *   - 生产环境：通常关闭此选项提高性能
         *   - 质量监控：在关键场景开启此选项
         *
         *   注意事项：
         *   - 开启此选项会增加响应时间
         *   - 需要配合topLogprobs参数使用
         *   - 建议仅在必要时开启
         * </pre>
         */
        private Boolean logprobs;

        /**
         * 基础参数：返回最可能token的数量
         * <pre>
         *   功能说明：
         *   1. 取值范围：0到20
         *   2. 指定在每个token位置返回的最可能token数量
         *   3. 每个返回的token都包含其对数概率
         *
         *   使用场景：
         *   - 模型分析：了解模型在每一步的候选输出
         *   - 质量控制：评估模型输出的确定性
         *   - 调试优化：分析模型的决策过程
         *
         *   实际应用建议：
         *   - 开发阶段：使用较大值（10-20）进行详细分析
         *   - 测试阶段：使用中等值（5-10）进行基本评估
         *   - 生产环境：通常使用较小值（1-5）或关闭
         *
         *   注意事项：
         *   - 必须配合logprobs=true使用
         *   - 值越大，返回信息越详细，但性能开销也越大
         *   - 建议根据实际需求选择合适的值
         * </pre>
         */
        private Integer topLogprobs;

        /**
         * 基础参数：是否启用思考模式（Anthropic模型特有）
         */
        private Boolean enableThinking;

        /**
         * 基础参数：思考预算令牌数（Anthropic模型特有）
         */
        private Integer thinkingBudgetTokens;

    }

    /**
     * 模型路由配置
     */
    @Data
    public static class RoutingConfig {

        /**
         * 是否启用场景路由与自动降级链
         */
        private boolean enabled = false;

        /**
         * 主模型失败时是否尝试 fallback 链中的后续模型
         */
        private boolean fallbackEnabled = true;

        /**
         * 全局 fallback 模型链（在主模型失败后追加，去重）
         */
        private List<String> fallbackModels = new ArrayList<>();

        /**
         * 按场景（scene）选择模型链，按顺序尝试
         */
        private Map<String, List<String>> sceneRules = new java.util.LinkedHashMap<>();
    }

    /**
     * 熔断与重试相关配置
     */
    @Data
    public static class ResilienceConfig {

        /**
         * 是否启用简易熔断（连续失败后临时跳过该模型）
         */
        private boolean circuitBreakerEnabled = true;

        /**
         * 触发熔断的连续失败次数
         */
        private int failureThreshold = 3;

        /**
         * 熔断打开时长（毫秒）
         */
        private long openDurationMs = 60_000L;
    }

    /**
     * 健康检查配置
     */
    @Data
    public static class HealthCheckConfig {

        /**
         * probe 时是否发起真实 LLM 调用（false 则仅检查 ChatClient 是否存在）
         */
        private boolean liveProbe = true;

        /**
         * live probe 使用的最大输出 token
         */
        private int probeMaxTokens = 1;
    }
}
