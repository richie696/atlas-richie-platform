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

