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

import java.util.concurrent.CompletableFuture;

/**
 * 重排序（Rerank）模型抽象。
 * <p>
 * 多模态 R-N 设计中的重排能力抽象：输入查询与候选文档列表，模型按相关性打分并返回排序后的结果。
 * 不绑定具体厂商（DashScope / Cohere / Jina 等），业务侧只依赖此接口。
 *
 * @author richie696
 * @since 1.0.0
 */
public interface RerankModel {

    /**
     * 同步重排序。
     *
     * @param request 重排序请求（必含 query 与 documents）
     * @return 统一响应，包含按相关性降序的重排序结果
     */
    RerankResponse rerank(RerankRequest request);

    /**
     * 异步重排序，返回 {@link CompletableFuture} 以便调用方链式编排。
     *
     * @param request 重排序请求（必含 query 与 documents）
     * @return 异步结果，完成时携带统一响应
     */
    CompletableFuture<RerankResponse> rerankAsync(RerankRequest request);
}