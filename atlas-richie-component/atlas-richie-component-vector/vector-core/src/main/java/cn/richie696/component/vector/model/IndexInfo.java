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
package cn.richie696.component.vector.model;

import java.time.Instant;
import java.util.Map;

/**
 * 索引完整描述信息。
 *
 * <p>RAG 流程中由 {@code VectorService.describeIndex / getIndexInfo / listIndexes} 返回的"统一观察"。
 * 它把不同 provider 的原生元数据归一为同一形状（name/modality/dimension/metric/...），让上层
 * 业务可以做"展示给用户"、"做跨 provider 对账"、"做健康检查"而不需要写 7 套解析代码。</p>
 *
 * <p>关键不变量：{@link #documentCount} 是 <b>provider-specific 粗略估计</b>，不同 provider
 * 精度差异很大（Milvus 可精确，Redis 走 {@code FT.INFO} 近似，Weaviate 也走估算）；上层在做
 * "显示文档数"时务必标注"约"或"估"。{@link #metadata} 用来承载 provider 特有字段
 * （replicas、shards、distanceParams 等）。</p>
 *
 * <p>调用关系：{@code AbstractVectorService} 各 provider 子类（{@code MilvusVectorServiceImpl} 等）
 * 把自家 SDK 的 {@code describeIndex / getCollectionInfo} 返回值映射到本 record；{@link cn.richie696.component.vector.exceptions.VectorStoreNotExistException}
 * 在底层索引不存在时抛出，而不是返回本类。</p>
 *
 * @param name          索引名；与 {@link VectorRecord#indexName} / {@code IndexConfig.name}
 *                      一一对应。RAG 上层通常用此名做"索引列表"展示、"按索引下钻"等 UI 行为。
 * @param modality      该索引支持的模态（{@code TEXT / IMAGE}）；检索时只能 query 与索引模态
 *                      一致的 {@link VectorRecord}。
 * @param dimension     向量维度；必须与 EmbeddingModel 输出一致，变更模型时通常需要重建索引。
 * @param metric        距离度量方式（{@code cosine / euclidean / dot}）；语义差异与
 *                      {@link SearchOptions#minScore} 方向密切相关，做相似度阈值时务必对齐。
 * @param indexType     索引类型（{@code hnsw / ivf / flat}）；不同 provider 还会暴露自家特有的
 *                      算法名（如 Milvus 的 {@code IVF_FLAT} / {@code HNSW}），由 provider
 *                      适配器映射到这三个语义类别之一。
 * @param status        索引当前状态；见 {@link IndexStatus}，{@code UNKNOWN} 通常表示
 *                      provider 暂时无法获取状态（未实现、健康检查失败），不要当作"已删除"。
 * @param documentCount 索引内文档数（粗略估计，provider-specific）；不同 provider 上精度差异
 *                      显著，UI 展示务必标注"约"。
 * @param createdAt     创建时间；由 provider 报告，不存在或不可知时为 {@code null}。
 * @param updatedAt     最后更新时间；含义因 provider 而异（schema 最后变更 vs 最近一次数据
 *                      写入），调用方应留意。
 * @param metadata      额外元信息（replicas / shards / distance params / 压缩配置等），
 *                      provider 特有字段的归一化容器；跨 provider 行为不应假设这些字段都存在。
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
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