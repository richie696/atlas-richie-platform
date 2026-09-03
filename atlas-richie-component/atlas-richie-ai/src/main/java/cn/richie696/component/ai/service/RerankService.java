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
package cn.richie696.component.ai.service;

import cn.richie696.component.ai.api.RerankResponse;

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
     * 带原生候选 ID 的重排入口。默认委托旧接口，保持现有厂商兼容；
     * 需要候选 ID 的厂商（如 Viking AI Search）由实现覆盖。
     */
    default RerankResponse rerank(String query, List<String> documents, List<String> documentIds,
                                   String model, Integer topN) {
        return rerank(query, documents, model, topN);
    }

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

    default CompletableFuture<RerankResponse> rerankAsync(String query, List<String> documents,
                                                           List<String> documentIds, String model, Integer topN) {
        return rerankAsync(query, documents, model, topN);
    }
}
