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
package com.richie.component.ai.service.impl;

import com.richie.component.ai.api.RerankModel;
import com.richie.component.ai.api.RerankRequest;
import com.richie.component.ai.api.RerankResponse;
import com.richie.component.ai.api.RerankResult;
import com.richie.component.ai.service.RerankService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 向量检索重排序业务编排服务。
 * <p>
 * 本类位于 vector 包，承担"业务入口"角色：接收查询与候选文档，委托 {@link RerankModel}（ai 包）完成实际打分，
 * 再把结果按 {@code index} 回填原始文档文本，保证调用方拿到的每条结果都携带原始内容。
 * <p>
 * 不绑定具体厂商（DashScope / Cohere / Jina …），由 ai 模块的 {@code @Bean("aiRerankModel")} 注入。
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
@Service
public class RerankServiceImpl implements RerankService {

    private final RerankModel rerankModel;

    /**
     * 构造 RerankService。
     *
     * @param rerankModel 重排序模型抽象，可为 {@code null}（ai 模块未启用 / 未配置模型时）。
     *                    注入为 {@code required = false} 以保持非破坏性：未启用重排序能力的业务方可正常启动，
     *                    调用 {@link #rerank} / {@link #rerankAsync} 时再以 IllegalStateException 提示。
     */
    @Autowired(required = false)
    public RerankServiceImpl(RerankModel rerankModel) {
        this.rerankModel = rerankModel;
    }

    /**
     * 同步重排序。
     *
     * @param query     查询文本（必填）
     * @param documents 候选文档列表（必填，至少 1 条）
     * @param model     重排序模型名（可选；为空则由实现端选择默认模型）
     * @param topN      返回 Top-N（可选；为空则由实现端返回全量）
     * @return 按相关性降序的重排序结果；每条结果的 {@code document} 若为空则回填入参对应下标的原文
     * @throws IllegalArgumentException 当 query / documents 非法
     * @throws IllegalStateException    当未注入 {@link RerankModel} 时
     */
    public RerankResponse rerank(String query, List<String> documents, String model, Integer topN) {
        validate(query, documents);
        RerankModel modelRef = requireModel();
        RerankRequest request = new RerankRequest(query, documents, model, topN);
        RerankResponse resp = modelRef.rerank(request);
        if (resp.isSuccess() && resp.getResults() != null) {
            enrichDocuments(resp.getResults(), documents);
        }
        return resp;
    }

    /**
     * 异步重排序。
     *
     * @param query     查询文本（必填）
     * @param documents 候选文档列表（必填，至少 1 条）
     * @param model     重排序模型名（可选）
     * @param topN      返回 Top-N（可选）
     * @return 异步结果，完成时携带按相关性降序的重排序结果（同样回填原文）
     * @throws IllegalArgumentException 当 query / documents 非法
     * @throws IllegalStateException    当未注入 {@link RerankModel} 时
     */
    public CompletableFuture<RerankResponse> rerankAsync(String query, List<String> documents, String model, Integer topN) {
        validate(query, documents);
        RerankModel modelRef = requireModel();
        RerankRequest request = new RerankRequest(query, documents, model, topN);
        return modelRef.rerankAsync(request).thenApply(resp -> {
            if (resp.isSuccess() && resp.getResults() != null) {
                enrichDocuments(resp.getResults(), documents);
            }
            return resp;
        });
    }

    /**
     * 入参校验。
     *
     * @param query     查询文本
     * @param documents 候选文档列表
     */
    private void validate(String query, List<String> documents) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query 不能为空");
        }
        if (documents == null || documents.isEmpty()) {
            throw new IllegalArgumentException("documents 不能为空");
        }
    }

    /**
     * 校验 {@link RerankModel} 已注入。
     *
     * @return 已注入的 {@link RerankModel}
     * @throws IllegalStateException 当未注入时（ai 模块未启用 / 未配置默认模型）
     */
    private RerankModel requireModel() {
        if (rerankModel == null) {
            throw new IllegalStateException(
                    "RerankModel 未注入：请确认 atlas-richie-component-ai 已引入且已配置默认模型（aiRerankModel Bean 未生成）");
        }
        return rerankModel;
    }

    /**
     * 按 {@link RerankResult#getIndex()} 回填原始文档文本。
     * <p>
     * 多数厂商响应只携带 {@code relevance_score} 与 {@code index}，不重复回传原文。
     * 这里以入参 {@code documents} 为权威，回填到 {@link RerankResult#getDocument()} 字段，
     * 调用方可以直接消费，无需再做一次列表对位。
     *
     * @param results    厂商返回的重排序结果（可能为 {@code null}）
     * @param documents  原始候选文档列表
     * @return 回填后的重排序结果；当入参为 {@code null} 时返回空列表
     */
    private List<RerankResult> enrichDocuments(List<RerankResult> results, List<String> documents) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        for (RerankResult result : results) {
            if (result == null) {
                continue;
            }
            if (result.getDocument() == null || result.getDocument().isEmpty()) {
                int idx = result.getIndex();
                if (idx >= 0 && idx < documents.size()) {
                    result.setDocument(Objects.requireNonNullElse(documents.get(idx), ""));
                }
            }
        }
        log.debug("RerankService enriched {} results against {} input documents",
                results.size(), documents.size());
        return results;
    }
}
