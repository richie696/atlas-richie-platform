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
package com.richie.component.vector.model;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 向量搜索结果模型
 * 用于封装向量搜索返回的结果
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
@Data
@Accessors(chain = true)
public class VectorSearchResult {

    /**
     * 文档ID
     */
    private String id;

    /**
     * 文档内容
     */
    private String content;

    /**
     * 相似度分数
     * 取值范围：0.0 - 1.0，越高表示越相似
     */
    private Double score;

    /**
     * 向量数据（可选）
     */
    private float[] vector;

    /**
     * 文档类型
     */
    private String type;

    /**
     * 文档标签
     */
    private String[] tags;

    /**
     * 文档来源
     */
    private String source;

    /**
     * 创建时间
     */
    private String createdAt;

    /**
     * 更新时间
     */
    private String updatedAt;

    /**
     * 文档分数
     */
    private Double documentScore;

    /**
     * 文档状态
     */
    private String status;

    /**
     * 自定义元数据
     */
    private Object metadata;

    /**
     * 命名空间
     */
    private String namespace;

    /**
     * 创建搜索结果
     */
    public static VectorSearchResult of(String id, String content, Double score) {
        return new VectorSearchResult()
                .setId(id)
                .setContent(content)
                .setScore(score);
    }

    /**
     * 创建带向量的搜索结果
     */
    public static VectorSearchResult of(String id, String content, Double score, float[] vector) {
        return of(id, content, score).setVector(vector);
    }
}
