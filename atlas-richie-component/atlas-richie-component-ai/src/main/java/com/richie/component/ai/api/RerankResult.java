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

/**
 * 单条重排序结果。
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
public class RerankResult {

    /**
     * 原文档在入参列表中的下标。
     */
    private int index;

    /**
     * 对应的原始文档内容（可能为空，取决于具体厂商响应是否回传）。
     */
    private String document;

    /**
     * 相关性分数，越大表示越相关；具体语义（0-1 / logit）由厂商决定。
     */
    private double relevanceScore;

    public RerankResult() {
    }

    /**
     * 全参构造器。
     *
     * @param index          文档在入参列表中的下标
     * @param document       对应文档内容（可为 null）
     * @param relevanceScore 相关性分数
     */
    public RerankResult(int index, String document, double relevanceScore) {
        this.index = index;
        this.document = document;
        this.relevanceScore = relevanceScore;
    }

}