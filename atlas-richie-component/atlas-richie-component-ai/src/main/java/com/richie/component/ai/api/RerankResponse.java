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
package com.richie.component.ai.api;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Clock;
import java.util.List;

/**
 * 重排序（Rerank）响应。
 * <p>
 * R-N §3.1 设计: 封装 {@link RerankResult} 列表,继承 {@link AiModelResponse} 的公共字段,
 * 统一返回结构。各厂商实现的原始响应先映射为 {@link RerankResult} 列表,
 * 再组装进本类型返回给调用方。
 * <p>
 * 使用 {@link #succeed(List, Clock)} 或 {@link #failed(String, String, Clock)} 工厂方法构建。
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RerankResponse extends AiModelResponse<RerankResponse> {

    /** 按相关性降序的重排序结果列表。 */
    private List<RerankResult> results;

    /**
     * 构建成功响应。
     *
     * @param results 重排序结果列表
     * @param clock   时钟
     * @return 成功响应
     */
    public static RerankResponse succeed(List<RerankResult> results, Clock clock) {
        RerankResponse resp = new RerankResponse();
        resp.results = results;
        return AiModelResponse.succeed(resp, clock);
    }

    /**
     * 构建失败响应。
     *
     * @param errorCode    错误码
     * @param errorMessage 错误描述
     * @param clock        时钟
     * @return 失败响应
     */
    public static RerankResponse failed(String errorCode, String errorMessage, Clock clock) {
        RerankResponse resp = new RerankResponse();
        return AiModelResponse.failed(resp, errorCode, errorMessage, clock);
    }
}
