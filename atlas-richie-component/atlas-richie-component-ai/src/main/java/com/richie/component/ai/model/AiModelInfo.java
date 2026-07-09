/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.ai.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * AI模型信息类
 * 用于描述和管理AI模型的基本信息、状态和能力
 * 这个类就像是AI模型的"身份证"，记录了模型的所有重要信息
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
@Data
@Accessors(chain = true)
public class AiModelInfo {

    /**
     * 模型名称
     * 例如：gpt-4、claude-3-sonnet、deepseek-chat等
     * 这是AI模型的唯一标识符，就像人的名字一样
     * 在调用AI服务时，需要指定使用哪个模型
     */
    private String name;

    /**
     * 模型提供商
     * 例如：OPENAI、ANTHROPIC、DEEPSEEK、ZHIPUAI等
     * 表示这个AI模型是由哪家公司或组织开发的
     * 不同的提供商有不同的API接口和计费方式
     */
    private String provider;

    /**
     * 模型描述
     * 例如："OpenAI GPT-4模型，支持多轮对话和代码生成"
     * 对模型功能的简要说明，帮助用户了解模型的特点和适用场景
     * 就像产品的说明书一样
     */
    private String description;

    /**
     * 模型是否可用
     * true表示模型可以正常使用，false表示模型暂时不可用
     * 这个状态会根据网络连接、API密钥有效性、服务状态等因素动态更新
     * 在调用AI服务前，建议先检查这个状态
     */
    private boolean available;

    /**
     * 是否为默认模型
     * true表示这是系统默认使用的AI模型，false表示需要手动指定使用
     * 当用户没有明确指定使用哪个模型时，系统会自动使用默认模型
     * 通常设置为最稳定、最常用的模型
     */
    private boolean defaultModel;

    /**
     * 模型类型
     * 表示这个AI模型的主要功能类型
     * 不同类型的模型适用于不同的任务场景
     * 例如：聊天模型适合对话，文本生成模型适合写作，嵌入模型适合语义分析
     */
    private ModelType type;

    /**
     * 模型能力配置
     * 详细描述这个模型支持哪些参数和功能
     * 例如：是否支持调整温度参数、是否支持设置最大输出长度等
     * 这些信息帮助开发者了解如何更好地使用这个模型
     */
    private ModelCapabilities capabilities;

    /**
     * 最后检查时间
     * 记录最后一次检查模型状态的时间
     * 用于判断模型状态信息的时效性
     * 如果时间太久远，可能需要重新检查模型状态
     */
    private LocalDateTime lastChecked;

    /**
     * 错误信息（当模型不可用时）
     * 当模型不可用时，这里会记录具体的错误原因
     * 例如："API密钥无效"、"网络连接失败"、"服务暂时不可用"等
     * 帮助开发者快速定位和解决问题
     */
    private String errorMessage;

    /**
     * 构造器
     */
    public AiModelInfo() {
    }

    /**
     * 模型类型枚举
     * 定义了AI模型的主要分类，每种类型适用于不同的使用场景
     */
    public enum ModelType {
        /**
         * 聊天模型
         * 专门用于多轮对话的AI模型
         * 特点：能够理解上下文，支持连续对话，适合客服、助手等场景
         * 例如：ChatGPT、Claude等
         */
        CHAT,

        /**
         * 文本生成模型
         * 专门用于生成文本内容的AI模型
         * 特点：能够根据提示生成文章、故事、报告等长文本
         * 例如：GPT-3、BLOOM等
         */
        TEXT_GENERATION,

        /**
         * 嵌入模型
         * 将文本转换为数字向量的AI模型
         * 特点：能够计算文本之间的相似度，用于搜索、推荐、分类等
         * 例如：text-embedding-ada-002等
         */
        EMBEDDING,

        /**
         * 图像生成模型
         * 根据文本描述生成图像的AI模型
         * 特点：能够将文字描述转换为相应的图片
         * 例如：DALL-E、Midjourney等
         */
        IMAGE_GENERATION,

        /**
         * 多模态模型
         * 能够同时处理文本、图像、音频等多种数据类型的AI模型
         * 特点：功能全面，但可能在单一任务上不如专门模型
         * 例如：GPT-4V、Claude-3等
         */
        MULTIMODAL
    }

    /**
     * 模型能力详细配置类
     * 描述AI模型支持的具体功能和参数
     * 这些信息帮助开发者了解如何优化模型的使用效果
     */
    @Data
    @Accessors(chain = true)
    public static class ModelCapabilities {

        /**
         * 支持的最大输入令牌数
         * 令牌是AI模型处理文本的基本单位，大约1个中文汉字=1个令牌，1个英文单词=1-2个令牌
         * 这个值决定了你一次可以输入多长的文本给AI模型
         * 例如：如果maxInputTokens=4096，那么你最多可以输入约4000个汉字或2000个英文单词
         */
        private Integer maxInputTokens;

        /**
         * 支持的最大输出令牌数
         * 决定了AI模型一次最多能生成多长的回复
         * 例如：如果maxOutputTokens=2048，那么AI的回复最多约2000个汉字
         * 注意：实际输出可能比这个值短，这只是一个上限
         */
        private Integer maxOutputTokens;

        /**
         * 是否支持温度参数（temperature）
         * 温度参数控制AI回复的随机性和创造性
         * 低温度（0.1-0.3）：回复更确定、一致，适合事实性任务
         * 高温度（0.7-1.0）：回复更随机、有创意，适合创意写作
         * 如果为false，说明这个模型不支持调整温度
         */
        private boolean supportsTemperature;

        /**
         * 是否支持Top P参数
         * Top P参数控制AI回复的多样性
         * 低值（0.1-0.3）：回复更聚焦、确定
         * 高值（0.7-1.0）：回复更多样、有创意
         * 通常与温度参数配合使用，建议只调整其中一个
         */
        private boolean supportsTopP;

        /**
         * 是否支持Top K参数
         * Top K参数限制每一步只考虑概率最高的K个选项
         * 这是Anthropic Claude模型特有的参数
         * 低值（10-20）：回复更聚焦
         * 高值（40-50）：回复更多样
         */
        private boolean supportsTopK;

        /**
         * 是否支持频率惩罚（frequency penalty）
         * 频率惩罚控制AI是否重复使用相同的词汇
         * 正值（0.1-2.0）：避免重复，让回复更丰富
         * 负值（-2.0-0）：允许重复，保持主题一致性
         * 适用于需要避免词汇重复的场景
         */
        private boolean supportsFrequencyPenalty;

        /**
         * 是否支持存在惩罚（presence penalty）
         * 存在惩罚控制AI是否讨论新话题
         * 正值（0.1-2.0）：鼓励讨论新话题，适合头脑风暴
         * 负值（-2.0-0）：保持话题聚焦，适合深度分析
         * 与频率惩罚的区别：频率惩罚关注词汇重复，存在惩罚关注话题变化
         */
        private boolean supportsPresencePenalty;

        /**
         * 是否支持停止词设置
         * 停止词是AI生成时遇到就会停止的词汇
         * 例如：设置停止词为"结束"，AI生成到"结束"就会停止
         * 用于控制输出长度或避免生成特定内容
         * 通常最多可以设置4个停止词
         */
        private boolean supportsStop;

        /**
         * 是否支持对数概率输出
         * 对数概率是AI对每个生成词汇的置信度评分
         * 用于分析AI的决策过程，了解AI为什么选择某个词汇
         * 主要用于调试和分析，一般用户不需要使用
         */
        private boolean supportsLogprobs;

        /**
         * 是否支持思考模式（thinking mode）
         * 思考模式是Anthropic Claude模型的特有功能
         * 让AI在回复前先进行内部思考，提高回复质量
         * 适用于需要深度推理的复杂问题
         * 会消耗额外的令牌数
         */
        private boolean supportsThinking;

        /**
         * 默认构造函数
         * 创建一个新的模型能力配置对象，所有能力默认为false
         */
        public ModelCapabilities() {
        }
    }

    /**
     * 创建模型信息对象
     * 这是一个静态工厂方法，用于快速创建模型信息实例
     *
     * @param name     模型名称，例如"gpt-4"
     * @param provider 提供商，例如"OPENAI"
     * @return 配置好的模型信息对象
     */
    public static AiModelInfo of(String name, String provider) {
        return new AiModelInfo()
                .setName(name)
                .setProvider(provider)
                .setType(ModelType.CHAT)  // 默认设置为聊天模型
                .setLastChecked(LocalDateTime.now());  // 设置当前时间为检查时间
    }

    /**
     * 创建可用的模型信息对象
     * 用于表示一个可以正常使用的AI模型
     *
     * @param name     模型名称
     * @param provider 提供商
     * @return 设置为可用的模型信息对象
     */
    public static AiModelInfo available(String name, String provider) {
        return of(name, provider).setAvailable(true);
    }

    /**
     * 创建不可用的模型信息对象
     * 用于表示一个暂时无法使用的AI模型
     *
     * @param name        模型名称
     * @param provider    提供商
     * @param errorMessage 错误信息，说明为什么模型不可用
     * @return 设置为不可用的模型信息对象
     */
    public static AiModelInfo unavailable(String name, String provider, String errorMessage) {
        return of(name, provider)
                .setAvailable(false)
                .setErrorMessage(errorMessage);
    }
}
