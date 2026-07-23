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
 * 搜索选项。
 * <p>
 * 用于在 {@code searchByText} / {@code searchByImage} / {@code hybridSearch} 等方法中传递精细控制参数。
 *
 * @author richie696
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchOptions {

    /**
     * 是否执行 rerank（默认 true；图片搜索时被忽略 — CLIP 双塔已对齐语义）
     */
    @Builder.Default
    private Boolean rerank = true;

    /**
     * 最小相似度阈值（0-1）
     */
    private Double minScore;

    /**
     * 元数据过滤表达式（provider-specific DSL）
     */
    private String filterExpression;

    /**
     * 命名空间隔离
     */
    private String namespace;

    /**
     * 返回结果数限制
     */
    private Integer limit;

    /**
     * 文档类型过滤（type == this）
     */
    private String type;
}