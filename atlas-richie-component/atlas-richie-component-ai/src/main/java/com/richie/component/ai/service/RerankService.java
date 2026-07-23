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
package com.richie.component.ai.service;

import com.richie.component.ai.api.RerankResponse;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 重排序服务接口 — vector-core 定义的门面抽象。
 *
 * @author richie696
 * @since 1.0.0
 */
public interface RerankService {

    /**
     * 同步重排序。
     *
     * @param query     查询文本
     * @param documents 候选文档列表
     * @param model     重排序模型名（可选）
     * @param topN      返回 Top-N（可选）
     * @return 重排序结果
     */
    RerankResponse rerank(String query, List<String> documents, String model, Integer topN);

    /**
     * 异步重排序。
     *
     * @param query     查询文本
     * @param documents 候选文档列表
     * @param model     重排序模型名（可选）
     * @param topN      返回 Top-N（可选）
     * @return 异步重排序结果
     */
    CompletableFuture<RerankResponse> rerankAsync(String query, List<String> documents, String model, Integer topN);
}
