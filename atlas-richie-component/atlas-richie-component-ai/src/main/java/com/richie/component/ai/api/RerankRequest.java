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

import java.util.List;

/**
 * 重排序请求。
 *
 * @author richie696
 * @since 1.0.0
 */
public class RerankRequest {

    /**
     * 查询文本（必填）。
     */
    private String query;

    /**
     * 待重排的候选文档列表（必填，至少 1 条）。
     */
    private List<String> documents;

    /**
     * 重排序模型名（可选，例如 "gte-rerank"），为空时由实现端选择默认模型。
     */
    private String model;

    /**
     * 返回 Top-N 结果（可选），为空则由实现端返回全量。
     */
    private Integer topN;

    public RerankRequest() {
    }

    /**
     * 全参构造器。
     *
     * @param query     查询文本
     * @param documents 候选文档列表
     * @param model     模型名（可选）
     * @param topN      返回 Top-N（可选）
     */
    public RerankRequest(String query, List<String> documents, String model, Integer topN) {
        this.query = query;
        this.documents = documents;
        this.model = model;
        this.topN = topN;
    }

    /**
     * Builder 风格构造器（等价于全参构造器，供链式调用）。
     *
     * @param query     查询文本
     * @param documents 候选文档列表
     * @param model     模型名（可选）
     * @param topN      返回 Top-N（可选）
     */
    public static RerankRequest of(String query, List<String> documents, String model, Integer topN) {
        return new RerankRequest(query, documents, model, topN);
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public List<String> getDocuments() {
        return documents;
    }

    public void setDocuments(List<String> documents) {
        this.documents = documents;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Integer getTopN() {
        return topN;
    }

    public void setTopN(Integer topN) {
        this.topN = topN;
    }
}