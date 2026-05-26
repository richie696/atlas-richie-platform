package com.richie.component.ai.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

/**
 * AI请求模型类
 * 用于封装发送给AI模型的所有请求信息
 * 这个类就像是给AI的"任务单"，告诉AI你想要它做什么
 * <p>
 * 使用场景：
 * - 发送问题给AI获取回答
 * - 让AI生成文章、代码、报告等
 * - 进行多轮对话
 * - 设置AI的行为参数
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
@Data
@Accessors(chain = true)
public class AiRequest {

    /**
     * 默认构造函数
     * 初始化AI请求对象
     */
    public AiRequest() {
    }

    /**
     * 模型名称（可选参数）
     * 指定要使用哪个AI模型，例如："gpt-4"、"claude-3-sonnet"、"deepseek-chat"
     * 如果不指定，系统会使用默认的AI模型
     * 就像选择不同的工具一样，不同的模型有不同的特点和能力
     */
    private String modelName;

    /**
     * 消息列表
     * 这是与AI对话的核心内容，包含所有的对话消息
     * 消息按时间顺序排列，AI会根据这些消息理解上下文并生成回复
     * 就像人类对话一样，AI需要知道之前的对话内容才能给出合适的回复
     */
    private List<Message> messages;

    /**
     * 模型参数配置（可选参数）
     * 用于调整AI模型的行为，就像调节机器人的"性格"一样
     * 例如：控制回复的创造性、长度、随机性等
     * 如果不设置，会使用AI模型的默认参数
     */
    private ModelOptions options;

    /**
     * 请求元数据（可选参数）
     * 用于存储额外的请求信息，例如：用户ID、会话ID、请求来源等
     * 这些信息不会影响AI的回复内容，主要用于日志记录和系统管理
     * 就像给快递包裹贴标签一样，方便追踪和管理
     */
    private Map<String, Object> metadata;

    /**
     * 消息模型类
     * 表示单条对话消息，就像微信聊天中的一条消息
     * 每条消息都有角色（谁说的）和内容（说了什么）
     */
    @Data
    @Accessors(chain = true)
    public static class Message {

        /**
         * 默认构造函数
         * 初始化消息对象
         */
        public Message() {
        }

        /**
         * 消息角色
         * 表示这条消息是谁说的，有三种角色：
         * - "system": 系统消息，用于设置AI的行为规则和背景信息
         * - "user": 用户消息，用户说的话或提出的问题
         * - "assistant": 助手消息，AI之前的回复内容
         * <p>
         * 例如：
         * system: "你是一个专业的Java开发工程师"
         * user: "请帮我写一个Spring Boot控制器"
         * assistant: "好的，我来帮你写一个用户管理的控制器..."
         */
        private String role;

        /**
         * 消息内容
         * 具体的文本内容，就是实际说的话
         * 可以是问题、指令、回复、说明等任何文本内容
         * <p>
         * 例如：
         * - "你好，请介绍一下Java"
         * - "生成一个用户注册的API接口"
         * - "请用中文回答"
         */
        private String content;

        /**
         * 消息名称（可选参数）
         * 给消息起一个名字，用于标识或分类
         * 通常用于复杂对话中区分不同的消息
         * 例如：可以给消息命名为"问题1"、"代码示例"等
         */
        private String name;
    }

    /**
     * 模型选项配置类
     * 用于精细控制AI模型的行为参数
     * 这些参数就像调节音响的音量、音调一样，可以改变AI输出的特点
     */
    @Data
    @Accessors(chain = true)
    public static class ModelOptions {

        /**
         * 默认构造函数
         * 初始化模型选项配置对象
         */
        public ModelOptions() {
        }

        /**
         * 模型名称
         * 指定具体使用的AI模型版本
         * 例如："gpt-4"、"gpt-3.5-turbo"、"claude-3-sonnet-20240229"
         * 不同的模型有不同的能力和价格
         */
        private String model;

        /**
         * 最大输出令牌数
         * 限制AI回复的最大长度
         * 令牌是AI处理文本的单位，大约1个中文汉字=1个令牌
         * 例如：设置为1000，AI最多回复约1000个汉字
         * 设置较小值可以节省费用，设置较大值可以获得更详细的回复
         */
        private Integer maxTokens;

        /**
         * 温度参数
         * 控制AI回复的随机性和创造性
         * 取值范围：0.0 - 2.0（DeepSeek支持到2.0）
         * <p>
         * 具体效果：
         * - 0.0-0.3：回复非常确定、一致，适合事实性任务
         * - 0.4-0.7：平衡创造性和一致性，适合一般对话
         * - 0.8-1.2：更有创意，适合创意写作
         * - 1.3-2.0：高度随机，适合艺术创作
         * <p>
         * 建议：
         * - 代码生成：使用0.1-0.3
         * - 一般问答：使用0.4-0.7
         * - 创意写作：使用0.8-1.5
         */
        private Double temperature;

        /**
         * Top P参数
         * 控制AI回复的多样性，与温度参数类似但机制不同
         * 取值范围：0.0 - 1.0
         * <p>
         * 具体效果：
         * - 0.1-0.3：回复更聚焦、确定
         * - 0.4-0.7：平衡多样性和一致性
         * - 0.8-1.0：回复更多样、有创意
         * <p>
         * 注意：通常建议只调整temperature或topP中的一个，不要同时调整
         */
        private Double topP;

        /**
         * Top K参数（Anthropic Claude模型特有）
         * 限制每一步只考虑概率最高的K个选项
         * 取值范围：1 - 100
         * <p>
         * 具体效果：
         * - 10-20：回复更聚焦、一致
         * - 30-50：平衡多样性和一致性
         * - 60-100：回复更多样
         * <p>
         * 这是Claude模型的特色功能，其他模型不支持
         */
        private Integer topK;

        /**
         * 频率惩罚参数
         * 控制AI是否重复使用相同的词汇
         * 取值范围：-2.0 到 2.0
         * <p>
         * 具体效果：
         * - 正值（0.1-2.0）：避免重复词汇，让回复更丰富
         * - 负值（-2.0-0）：允许重复，保持主题一致性
         * - 0：不进行频率惩罚
         * <p>
         * 适用场景：
         * - 技术文档：使用正值避免术语重复
         * - 创意写作：可以使用负值增加主题连贯性
         */
        private Double frequencyPenalty;

        /**
         * 存在惩罚参数
         * 控制AI是否讨论新话题
         * 取值范围：-2.0 到 2.0
         * <p>
         * 具体效果：
         * - 正值（0.1-2.0）：鼓励讨论新话题，适合头脑风暴
         * - 负值（-2.0-0）：保持话题聚焦，适合深度分析
         * - 0：不进行存在惩罚
         * <p>
         * 与frequencyPenalty的区别：
         * - frequencyPenalty关注词汇重复
         * - presencePenalty关注话题变化
         */
        private Double presencePenalty;

        /**
         * 停止词列表
         * 设置AI生成时遇到就会停止的词汇
         * 最多可以设置4个停止词
         * <p>
         * 使用场景：
         * - 控制输出长度：设置"结束"、"完成"等
         * - 避免特定内容：设置不希望出现的词汇
         * - 格式化输出：确保输出符合特定格式
         * <p>
         * 例如：设置停止词为["结束", "完毕"]，AI生成到这些词就会停止
         */
        private List<String> stop;

        /**
         * 是否返回对数概率
         * 控制是否返回AI对每个生成词汇的置信度评分
         * 主要用于调试和分析AI的决策过程
         * <p>
         * 用途：
         * - 分析AI为什么选择某个词汇
         * - 调试AI回复质量问题
         * - 研究AI的行为模式
         * <p>
         * 注意：会消耗额外的令牌数，一般用户不需要使用
         */
        private Boolean logprobs;

        /**
         * Top对数概率数量
         * 当启用对数概率时，返回前N个最可能的词汇及其概率
         * 取值范围：0 - 20
         * <p>
         * 例如：设置为5，会返回AI认为最可能的5个词汇及其概率
         * 用于深入了解AI的决策过程
         */
        private Integer topLogprobs;

        /**
         * 是否启用思考模式（Anthropic Claude模型特有）
         * 让AI在回复前先进行内部思考，提高回复质量
         * <p>
         * 特点：
         * - 适用于需要深度推理的复杂问题
         * - 会消耗额外的令牌数
         * - 回复质量通常更高
         * - 只有Claude模型支持此功能
         */
        private Boolean enableThinking;

        /**
         * 思考预算令牌数（Anthropic Claude模型特有）
         * 当启用思考模式时，限制AI内部思考使用的最大令牌数
         * <p>
         * 设置建议：
         * - 简单问题：100-500令牌
         * - 复杂问题：500-2000令牌
         * - 非常复杂的问题：2000+令牌
         * <p>
         * 注意：思考令牌也会计入总费用
         */
        private Integer thinkingBudgetTokens;
    }

    /**
     * 创建简单的用户消息请求
     * 这是最常用的创建方式，适用于简单的问答场景
     * <p>
     * 使用场景：
     * - 简单的问答
     * - 单次请求
     * - 不需要特殊配置的情况
     *
     * @param content 用户的问题或指令
     * @return 配置好的AI请求对象
     *
     * 示例：
     * AiRequest request = AiRequest.ofUserMessage("请介绍一下Java语言");
     */
    public static AiRequest ofUserMessage(String content) {
        return new AiRequest()
                .setMessages(List.of(new Message().setRole("user").setContent(content)));
    }

    /**
     * 创建带系统提示的用户消息请求
     * 适用于需要设置AI行为规则的场景
     * <p>
     * 使用场景：
     * - 需要AI扮演特定角色（如医生、律师、程序员）
     * - 需要设置AI的行为规则
     * - 需要AI按照特定格式回复
     *
     * @param systemPrompt 系统提示，设置AI的行为规则
     * @param userMessage  用户消息，具体的问题或指令
     * @return 配置好的AI请求对象
     *
     * 示例：
     * AiRequest request = AiRequest.ofSystemAndUser(
     *     "你是一个专业的Java开发工程师，请用简洁的语言回答",
     *     "请解释什么是Spring Boot"
     * );
     */
    public static AiRequest ofSystemAndUser(String systemPrompt, String userMessage) {
        return new AiRequest()
                .setMessages(List.of(
                        new Message().setRole("system").setContent(systemPrompt),
                        new Message().setRole("user").setContent(userMessage)
                ));
    }

    /**
     * 创建对话历史请求
     * 适用于多轮对话场景，保持对话的连续性
     * <p>
     * 使用场景：
     * - 多轮对话
     * - 需要AI记住之前对话内容
     * - 复杂的交互场景
     *
     * @param messages 完整的对话历史，包含所有消息
     * @return 配置好的AI请求对象
     *
     * <pre>{@code
     * 示例：
     * List<Message> history = Arrays.asList(
     *     new Message().setRole("user").setContent("你好"),
     *     new Message().setRole("assistant").setContent("你好！有什么可以帮助你的？"),
     *     new Message().setRole("user").setContent("我想学习Java")
     * );
     * AiRequest request = AiRequest.ofMessages(history);
     * }</pre>
     */
    public static AiRequest ofMessages(List<Message> messages) {
        return new AiRequest().setMessages(messages);
    }
}
