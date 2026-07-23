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
package com.richie.component.vector.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 混合搜索选项 — 向量 + 关键词双路召回。
 * <p>
 * 仅在支持混合搜索的 provider（Elasticsearch / Weaviate）下生效，
 * 其他 provider 默认实现退化为纯向量搜索并打日志。
 *
 * @author richie696
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HybridSearchOptions {

    /**
     * 向量召回权重（0-1），与 keywordWeight 之和应为 1
     */
    @Builder.Default
    private Double vectorWeight = 0.7;

    /**
     * 关键词召回权重（0-1）
     */
    @Builder.Default
    private Double keywordWeight = 0.3;

    /**
     * 关键词查询（BM25 / 全文检索）
     */
    private String keywordQuery;

    /**
     * 基础搜索选项（minScore / namespace / limit 等）
     */
    private SearchOptions searchOptions;
}