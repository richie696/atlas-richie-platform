package com.richie.component.ai.model;

import com.richie.component.ai.config.AiModelProperties;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 运行时模型配置
 * 用于业务系统从数据库读取后，动态初始化AI模型
 *
 * @author richie696
 * @version 1.0
 * @since 2026-04-22
 */
@Data
@Accessors(chain = true)
public class ModelOptions {

    /**
     * 模型唯一名称（组件内标识）
     */
    private String modelName;

    /**
     * 提供商名称（运行时字符串，不受枚举约束）
     * 示例：OPENAI、DEEPSEEK、ZHIPUAI、MINIMAX、MOONSHOT 或其他兼容OpenAI协议的值
     */
    private String provider;

    /**
     * API地址
     */
    private String baseUrl;

    /**
     * API密钥
     */
    private String apiKey;

    /**
     * 模型参数
     */
    private AiModelProperties.AiModelOptions options;
}

