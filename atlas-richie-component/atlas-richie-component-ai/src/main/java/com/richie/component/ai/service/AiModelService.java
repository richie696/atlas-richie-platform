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
package com.richie.component.ai.service;

import com.richie.component.ai.model.AiHealthResult;
import com.richie.component.ai.model.AiModelInfo;
import com.richie.component.ai.model.AiRequest;
import com.richie.component.ai.model.AiResponse;
import com.richie.component.ai.model.AiStreamChunk;
import com.richie.component.ai.model.ModelOptions;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * AI模型调用服务接口
 * 提供统一的AI模型调用能力，支持多模型动态切换
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-30
 */
public interface AiModelService {

    /**
     * 同步调用AI模型
     *
     * @param request AI请求对象
     * @return AI响应对象
     */
    AiResponse call(AiRequest request);

    /**
     * 异步调用AI模型
     *
     * @param request AI请求对象
     * @return 异步响应
     */
    CompletableFuture<AiResponse> callAsync(AiRequest request);

    /**
     * 流式调用AI模型
     *
     * @param request AI请求对象
     * @return 流式片段
     */
    Flux<AiStreamChunk> stream(AiRequest request);

    /**
     * 使用指定模型调用AI
     *
     * @param modelName 模型名称
     * @param request   AI请求对象
     * @return AI响应对象
     */
    AiResponse callWithModel(String modelName, AiRequest request);

    /**
     * 获取所有可用的AI模型信息
     *
     * @return 模型信息列表
     */
    List<AiModelInfo> getAvailableModels();

    /**
     * 获取指定模型信息
     *
     * @param modelName 模型名称
     * @return 模型信息
     */
    AiModelInfo getModelInfo(String modelName);

    /**
     * 检查模型是否可用
     *
     * @param modelName 模型名称
     * @return 是否可用
     */
    boolean isModelAvailable(String modelName);

    /**
     * 获取默认模型名称
     *
     * @return 默认模型名称
     */
    String getDefaultModel();

    /**
     * 设置默认模型
     *
     * @param modelName 模型名称
     */
    void setDefaultModel(String modelName);

    /**
     * 通过代码动态初始化模型配置
     * 适用于业务系统从数据库读取模型配置后进行运行时装载
     *
     * @param modelOptionsList 运行时模型配置列表
     */
    void initializeModels(List<ModelOptions> modelOptionsList);

    /**
     * 移除运行时或已注册的模型
     *
     * @param modelName 模型名称
     */
    void removeModel(String modelName);

    /**
     * 探测单个模型健康状态
     *
     * @param modelName 模型名称
     * @return 健康检查结果
     */
    AiHealthResult probe(String modelName);

    /**
     * 探测所有已注册模型
     *
     * @return 健康检查结果列表
     */
    List<AiHealthResult> probeAll();
}
