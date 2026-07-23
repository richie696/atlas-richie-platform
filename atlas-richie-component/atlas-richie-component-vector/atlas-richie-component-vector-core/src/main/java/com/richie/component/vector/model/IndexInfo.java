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

import java.time.Instant;
import java.util.Map;

/**
 * 索引完整描述信息。
 *
 * @param name          索引名
 * @param modality      该索引支持的模态（TEXT / IMAGE / MULTI）
 * @param dimension     向量维度
 * @param metric        距离度量方式（cosine / euclidean / dot）
 * @param indexType     索引类型（hnsw / ivf / flat）
 * @param status        索引当前状态
 * @param documentCount 索引内文档数（粗略估计，provider-specific）
 * @param createdAt     创建时间
 * @param updatedAt     最后更新时间
 * @param metadata      额外元信息（replicas / shards / provider-specific 配置等）
 * @author richie696
 * @since 2.0.0
 */
public record IndexInfo(
        String name,
        Modality modality,
        Integer dimension,
        String metric,
        String indexType,
        IndexStatus status,
        Long documentCount,
        Instant createdAt,
        Instant updatedAt,
        Map<String, Object> metadata
) {
}