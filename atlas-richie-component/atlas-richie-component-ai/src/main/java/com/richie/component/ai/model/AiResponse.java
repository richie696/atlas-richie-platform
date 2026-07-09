/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
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
import java.util.Map;

/**
 * AI响应模型类
 * 用于封装AI模型返回的所有响应信息
 * 这个类就像是AI的"回执单"，包含了AI的回复和相关的状态信息
 * <p>
 * 使用场景：
 * - 获取AI的回复内容
 * - 检查AI调用是否成功
 * - 分析AI的使用情况（如令牌消耗）
 * - 处理AI调用错误
 * - 监控AI服务的性能
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
@Data
@Accessors(chain = true)
public class AiResponse {

    /**
     * 默认构造函数
     * 初始化AI响应对象
     */
    public AiResponse() {
    }

    /**
     * 请求是否成功
     * true表示AI调用成功，false表示调用失败
     * 这是最重要的状态标识，在获取AI回复前应该先检查这个字段
     * <p>
     * 使用建议：
     * - 调用AI服务后，首先检查isSuccess()
     * - 只有成功时才读取content等字段
     * - 失败时应该查看errorMessage了解失败原因
     */
    private boolean success;

    /**
     * AI的回复内容
     * 这是AI实际生成的文本内容，是用户最关心的信息
     * 只有当success为true时，这个字段才有意义
     * <p>
     * 内容类型：
     * - 文本回答：AI对问题的回答
     * - 代码生成：AI生成的程序代码
     * - 文章创作：AI写的文章、报告等
     * - 翻译结果：AI翻译的文本
     * <p>
     * 示例：
     * - "Java是一种面向对象的编程语言..."
     * - "public class UserController { ... }"
     * - "这是一篇关于人工智能的文章..."
     */
    private String content;

    /**
     * 使用的模型名称
     * 记录这次调用实际使用的AI模型
     * 例如："gpt-4"、"claude-3-sonnet"、"deepseek-chat"
     * <p>
     * 用途：
     * - 确认使用了哪个模型
     * - 分析不同模型的性能
     * - 成本核算（不同模型价格不同）
     * - 日志记录和审计
     */
    private String modelName;

    /**
     * 模型提供商
     * 记录AI模型的开发公司或组织
     * 例如："OPENAI"、"ANTHROPIC"、"DEEPSEEK"、"ZHIPUAI"
     * <p>
     * 用途：
     * - 了解AI服务的来源
     * - 分析不同提供商的服务质量
     * - 合规性检查
     * - 技术支持联系
     */
    private String provider;

    /**
     * 响应时间
     * 记录AI回复生成的具体时间
     * 格式：年-月-日 时:分:秒
     * <p>
     * 用途：
     * - 记录AI服务的使用时间
     * - 分析AI服务的响应模式
     * - 审计和合规要求
     * - 用户行为分析
     */
    private LocalDateTime responseTime;

    /**
     * 请求耗时（毫秒）
     * 从发送请求到收到回复的总时间
     * 单位：毫秒（1秒 = 1000毫秒）
     * <p>
     * 性能分析：
     * - 0-1000ms：响应很快
     * - 1000-3000ms：响应正常
     * - 3000-10000ms：响应较慢
     * - >10000ms：响应很慢，可能需要优化
     * <p>
     * 用途：
     * - 监控AI服务性能
     * - 用户体验优化
     * - 成本效益分析
     * - 系统调优
     */
    private Long duration;

    /**
     * 令牌使用情况
     * 记录这次AI调用消耗的令牌数量
     * 令牌是AI处理文本的基本单位，也是计费的依据
     * <p>
     * 令牌说明：
     * - 1个中文汉字 ≈ 1个令牌
     * - 1个英文单词 ≈ 1-2个令牌
     * - 标点符号、空格等也计入令牌
     * <p>
     * 计费影响：
     * - 输入令牌：用户发送的内容
     * - 输出令牌：AI生成的内容
     * - 总令牌：输入+输出，决定费用
     */
    private Usage usage;

    /**
     * 错误信息（当调用失败时）
     * 当success为false时，这里会记录具体的错误原因
     * <p>
     * 常见错误类型：
     * - "API密钥无效"：需要检查API密钥配置
     * - "网络连接失败"：检查网络连接
     * - "模型不可用"：选择的模型暂时无法使用
     * - "请求超时"：网络或服务响应太慢
     * - "参数错误"：请求参数配置有问题
     * - "配额超限"：API调用次数或金额超限
     * <p>
     * 处理建议：
     * - 根据错误信息进行相应的处理
     * - 记录错误日志用于问题排查
     * - 向用户显示友好的错误提示
     */
    private String errorMessage;

    /**
     * 错误代码（当调用失败时）
     * 当success为false时，这里会记录标准的错误代码
     * <p>
     * 常见错误代码：
     * - "API_KEY_INVALID"：API密钥无效
     * - "NETWORK_ERROR"：网络错误
     * - "MODEL_UNAVAILABLE"：模型不可用
     * - "TIMEOUT"：请求超时
     * - "PARAMETER_ERROR"：参数错误
     * - "QUOTA_EXCEEDED"：配额超限
     * - "UNKNOWN_ERROR"：未知错误
     * <p>
     * 用途：
     * - 程序化错误处理
     * - 错误分类和统计
     * - 自动化故障恢复
     */
    private String errorCode;

    /**
     * 原始响应数据
     * 存储AI服务返回的完整原始数据
     * 包含所有技术细节，通常用于调试和高级分析
     * <p>
     * 内容可能包括：
     * - 完整的API响应
     * - 技术参数和配置
     * - 调试信息
     * - 服务端元数据
     * <p>
     * 使用场景：
     * - 技术调试和问题排查
     * - 高级功能开发
     * - 数据分析和挖掘
     * - 兼容性处理
     */
    private Map<String, Object> rawResponse;

    /**
     * 响应元数据
     * 存储额外的响应信息，用于扩展功能
     * <p>
     * 可能包含：
     * - 会话ID
     * - 请求ID
     * - 用户信息
     * - 业务相关数据
     * - 自定义标记
     * <p>
     * 用途：
     * - 会话管理
     * - 请求追踪
     * - 业务逻辑处理
     * - 数据关联
     */
    private Map<String, Object> metadata;

    /**
     * 令牌使用情况统计类
     * 详细记录AI调用过程中消耗的令牌数量
     * 这些信息对于成本控制和性能优化非常重要
     *
     * @author richie696
     * @version 1.0
     * @since 2025-07-01
     */
    @Data
    @Accessors(chain = true)
    public static class Usage {

        /**
         * 默认构造函数
         * 初始化令牌使用情况统计对象
         */
        public Usage() {
        }

        /**
         * 提示令牌数（输入令牌）
         * 用户发送给AI的内容消耗的令牌数量
         * 包括：问题、指令、上下文信息等
         * <p>
         * 计算方式：
         * - 中文：1个汉字 ≈ 1个令牌
         * - 英文：1个单词 ≈ 1-2个令牌
         * - 标点符号、空格等也计入
         * <p>
         * 优化建议：
         * - 精简问题描述，减少不必要的词汇
         * - 避免重复信息
         * - 合理控制上下文长度
         * <p>
         * 示例：
         * "请介绍一下Java语言" ≈ 8个令牌
         * "你好" ≈ 2个令牌
         */
        private Integer promptTokens;

        /**
         * 完成令牌数（输出令牌）
         * AI生成回复内容消耗的令牌数量
         * 包括：回答、代码、文章等AI生成的所有内容
         * <p>         * 影响因素：
         * - 回复内容的长度
         * - 内容的复杂度
         * - 模型的生成策略
         * - 设置的maxTokens参数
         * <p>         * 成本影响：
         * - 输出令牌通常比输入令牌更贵
         * - 长回复会显著增加成本
         * - 需要平衡质量和成本
         * <p>         * 示例：
         * 一个详细的Java介绍 ≈ 200个令牌
         * 一个简单的代码示例 ≈ 50个令牌
         */
        private Integer completionTokens;

        /**
         * 总令牌数
         * 整个AI调用过程消耗的总令牌数量
         * 计算公式：promptTokens + completionTokens
         * <p>         * 重要性：
         * - 这是计费的主要依据
         * - 不同模型的令牌价格不同
         * - 影响API调用的成本
         * <p>         * 成本计算示例：
         * - GPT-4：约$0.03/1000个令牌
         * - GPT-3.5：约$0.002/1000个令牌
         * - Claude-3：约$0.015/1000个令牌
         * <p>         * 优化策略：
         * - 监控令牌使用情况
         * - 优化提示词减少输入令牌
         * - 合理设置输出长度限制
         * - 选择合适的模型平衡质量和成本
         */
        private Integer totalTokens;
    }

    /**
     * 创建成功响应
     * 当AI调用成功时，使用这个静态方法创建响应对象
     * <p>
     * 使用场景：
     * - AI服务正常返回结果时
     * - 需要构造成功响应时
     * @param content   AI生成的回复内容
     * @param modelName 使用的模型名称
     * @param provider  模型提供商
     * @return 配置好的成功响应对象
     * <p>
     * 示例：
     * AiResponse response = AiResponse.success(
     *     "Java是一种面向对象的编程语言...",
     *     "gpt-4",
     *     "OPENAI"
     * );
     */
    public static AiResponse success(String content, String modelName, String provider) {
        return new AiResponse()
                .setSuccess(true)
                .setContent(content)
                .setModelName(modelName)
                .setProvider(provider)
                .setResponseTime(LocalDateTime.now());
    }

    /**
     * 创建失败响应
     * 当AI调用失败时，使用这个静态方法创建响应对象
     * <p>     * 使用场景：
     * - AI服务返回错误时
     * - 网络连接失败时
     * - 参数配置错误时
     * @param errorMessage 详细的错误信息
     * @param errorCode    标准的错误代码
     * @return 配置好的失败响应对象
     * <p>
     * 示例：
     * AiResponse response = AiResponse.failure(
     *     "API密钥无效，请检查配置",
     *     "API_KEY_INVALID"
     * );
     */
    public static AiResponse failure(String errorMessage, String errorCode) {
        return new AiResponse()
                .setSuccess(false)
                .setErrorMessage(errorMessage)
                .setErrorCode(errorCode)
                .setResponseTime(LocalDateTime.now());
    }

    /**
     * 创建失败响应（简化版）
     * 当不需要详细错误代码时，使用这个简化方法
     * <p>
     * 使用场景：
     * - 简单的错误处理
     * - 不需要分类错误类型时
     * @param errorMessage 错误信息
     * @return 配置好的失败响应对象（错误代码默认为"UNKNOWN_ERROR"）
     * <p>
     * 示例：
     * AiResponse response = AiResponse.failure("网络连接失败");
     */
    public static AiResponse failure(String errorMessage) {
        return failure(errorMessage, "UNKNOWN_ERROR");
    }
}
