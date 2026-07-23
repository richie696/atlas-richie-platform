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
package com.richie.component.vector.service;

import com.richie.component.vector.config.VectorProperties;
import com.richie.component.vector.model.BatchEvent;
import com.richie.component.vector.model.HybridSearchOptions;
import com.richie.component.vector.model.IndexInfo;
import com.richie.component.vector.model.SearchOptions;
import com.richie.component.vector.model.VectorRecord;
import com.richie.component.vector.model.VectorSearchResult;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 向量服务 v2 接口 — 多模态 + 批量异步 + 完整向量库能力。
 * <p>
 * <b>设计原则</b>：
 * <ul>
 *   <li><b>门面封装</b> — 调用方传入原始内容（文本/图片），VectorService 内部完成嵌入和入库</li>
 *   <li><b>模态分离</b> — 文本走 TextEmbeddingModel，图片走 ImageEmbeddingModel（CLIP/SigLIP）</li>
 *   <li><b>同步 + 异步</b> — 单条同步、批量走 {@link Flux} 反应式事件流</li>
 *   <li><b>v2 公开接口</b> — 本表列出所有公共方法（{@code addText/addImage/searchByText/searchByImage/addBatch/...}）</li>
 * </ul>
 *
 * <h2>典型用法</h2>
 * <pre>{@code
 * // 单条文本
 * String id = vectorService.addText("docs", "Java 是一种面向对象的语言", Map.of("tag", "tech"));
 *
 * // 单条图片
 * String id = vectorService.addImage("products", imageBytes, "image/png", Map.of("sku", "P001"));
 *
 * // 文本搜索（自动 rerank）
 * List<VectorSearchResult> results = vectorService.searchByText("docs", "Java 教程", 10);
 *
 * // 图片搜索（不 rerank）
 * List<VectorSearchResult> matches = vectorService.searchByImage("products", imageBytes, "image/png", 5);
 *
 * // 批量异步 + 实时进度
 * vectorService.addBatch("docs", Flux.fromIterable(records))
 *     .subscribe(event -> ui.update(event), error -> log.error(error));
 * }</pre>
 *
 * @author richie696
 * @since 2.0.0
 */
public interface VectorService {

    // ==================== 单条同步 — Add ====================

    /**
     * 添加文本记录（自动调用 TextEmbeddingModel）。
     *
     * @param indexName 目标索引
     * @param text      文本内容
     * @param metadata  业务元数据（可为 null）
     * @return 新记录 ID（系统生成 UUID）
     */
    String addText(String indexName, String text, Map<String, Object> metadata);

    /**
     * 添加统一记录（自动根据 content.modality() 路由）。
     *
     * @param record 记录（id 可为 null，自动生成）
     * @return 落库后的 ID
     */
    String add(VectorRecord record);

    /**
     * 添加图片记录（byte[] 形式）。
     *
     * @param indexName 目标索引
     * @param image     图片字节
     * @param mimeType  MIME 类型（image/png 等）
     * @param metadata  业务元数据（可为 null）
     * @return 新记录 ID
     */
    String addImage(String indexName, byte[] image, String mimeType, Map<String, Object> metadata);

    /**
     * 添加图片记录（Path 形式）。
     *
     * @param indexName  目标索引
     * @param imagePath  图片文件路径
     * @param mimeType   MIME 类型
     * @param metadata   业务元数据（可为 null）
     * @return 新记录 ID
     */
    String addImage(String indexName, Path imagePath, String mimeType, Map<String, Object> metadata);

    /**
     * 添加远程图片（异步下载后入库）。
     * <p>
     * 调用方提供 URL，VectorService 在入库前异步下载图片字节。
     * 下载失败会抛出 {@link RuntimeException}。
     */
    String addImageUrl(String indexName, String url, String mimeType, Map<String, Object> metadata);

    // ==================== 单条同步 — Update ====================

    /**
     * 更新文本内容（delete + insert 等价语义）。
     */
    void updateText(String indexName, String id, String text, Map<String, Object> metadata);

    /**
     * 更新统一记录。
     */
    void update(VectorRecord record);

    /**
     * 更新图片（byte[] 形式）。
     */
    void updateImage(String indexName, String id, byte[] image, String mimeType, Map<String, Object> metadata);

    /**
     * 更新图片（Path 形式）。
     */
    void updateImage(String indexName, String id, Path imagePath, String mimeType, Map<String, Object> metadata);

    // ==================== 单条同步 — Delete ====================

    /**
     * 按 ID 删除单条记录。
     */
    void delete(String indexName, String id);

    /**
     * 按谓词批量删除（慎用 — 内部需遍历索引评估谓词，性能较差）。
     *
     * @return 实际删除的记录数
     */
    long deleteIf(String indexName, Predicate<VectorRecord> filter);

    // ==================== 单条同步 — Get ====================

    /**
     * 按 ID 获取单条记录。
     */
    Optional<VectorRecord> get(String indexName, String id);

    /**
     * 按 ID 列表批量获取（保持顺序，跳过不存在的 ID）。
     */
    List<VectorRecord> getAll(String indexName, Collection<String> ids);

    // ==================== 单条同步 — Search ====================

    /**
     * 文本搜索（自动 rerank — 需 RerankService 已注入）。
     *
     * @param indexName 目标索引
     * @param text      查询文本
     * @param limit     返回 Top-K
     * @return 搜索结果（按相关性降序，rerank 后）
     */
    List<VectorSearchResult> searchByText(String indexName, String text, int limit);

    /**
     * 文本搜索（带相似度阈值 + 自动 rerank）。
     */
    List<VectorSearchResult> searchByText(String indexName, String text, int limit, double minScore);

    /**
     * 文本搜索（完整选项 — rerank 控制、过滤、命名空间等）。
     */
    List<VectorSearchResult> searchByText(String indexName, String text, int limit, SearchOptions options);

    /**
     * 图片搜索（不 rerank — CLIP 双塔已对齐语义空间）。
     *
     * @param indexName 目标索引
     * @param image     查询图片字节
     * @param mimeType  MIME 类型
     * @param limit     返回 Top-K
     * @return 搜索结果（按 CLIP 余弦相似度降序）
     */
    List<VectorSearchResult> searchByImage(String indexName, byte[] image, String mimeType, int limit);

    /**
     * 图片搜索（带相似度阈值）。
     */
    List<VectorSearchResult> searchByImage(String indexName, byte[] image, String mimeType, int limit, double minScore);

    /**
     * 图片搜索（Path 形式）。
     */
    List<VectorSearchResult> searchByImage(String indexName, Path imagePath, String mimeType, int limit);

    // ==================== 索引管理 — 基础 ====================

    /**
     * 创建索引（预创建 schema）。
     * <p>
     * 注意：很多 provider（Milvus / Qdrant）支持首次写入时自动创建索引，
     * 本方法主要用于显式控制 schema、维度、metric、分片副本数。
     */
    void createIndex(String indexName, VectorProperties.IndexConfig config);

    /**
     * 删除索引（含全部数据，慎用）。
     */
    void deleteIndex(String indexName);

    /**
     * 判断索引是否存在。
     */
    boolean indexExists(String indexName);

    /**
     * 获取索引配置（provider-specific 字段可能丢失）。
     */
    VectorProperties.IndexConfig getIndexConfig(String indexName);

    /**
     * 统计索引内文档数。
     */
    long countDocuments(String indexName);

    /**
     * 分页列出索引内文档。
     */
    List<VectorRecord> listDocuments(String indexName, int offset, int limit);

    // ==================== 索引管理 — 扩展 ====================

    /**
     * 列出所有索引（含元信息）。
     */
    List<IndexInfo> listIndexes();

    /**
     * 清空索引内全部数据（保留 schema）。
     *
     * @return 删除的文档数
     */
    long truncateIndex(String indexName);

    /**
     * 更新索引配置（部分 provider 支持在线修改）。
     */
    boolean updateIndexConfig(String indexName, VectorProperties.IndexConfig config);

    /**
     * 克隆索引（含数据）— 用于备份/灰度迁移。
     */
    boolean cloneIndex(String sourceIndexName, String targetIndexName);

    /**
     * 阻塞等待索引就绪（用于初始化流程）。
     *
     * @return true=就绪 / false=超时
     */
    boolean awaitIndexReady(String indexName, Duration timeout);

    /**
     * 描述索引完整信息（等价于 {@link #getIndexStats(String)}）。
     */
    IndexInfo describeIndex(String indexName);

    // ==================== 统计健康 ====================

    /**
     * 获取索引统计（文档数、状态、容量等）。
     */
    IndexInfo getIndexStats(String indexName);

    /**
     * 健康检查 — 索引可读可写。
     */
    boolean healthCheck(String indexName);

    // ==================== 高级搜索 ====================

    /**
     * 混合搜索（向量 + 关键词双路召回）。
     * <p>
     * 仅 Elasticsearch / Weaviate 等支持 BM25 的 provider 实现完整语义；
     * 其他 provider 退化为纯向量搜索并打日志。
     */
    List<VectorSearchResult> hybridSearch(String indexName, String text, String keywordQuery,
                                          int limit, HybridSearchOptions options);

    /**
     * 多向量搜索（同一查询用多 embedding 拼接后检索）— 用于 named vectors / multi-vector 场景。
     */
    List<VectorSearchResult> searchByMultiVector(String indexName, List<float[]> vectors, int limit);

    // ==================== 运维 / 别名 / 备份 ====================

    /**
     * 触发索引优化（重建、合并段等，provider-specific）。
     */
    boolean optimize(String indexName);

    /**
     * 创建别名（同一索引可有多个别名）。
     */
    boolean createAlias(String indexName, String alias);

    /**
     * 原子切换别名（蓝绿部署 / 灰度切换）。
     */
    boolean switchAlias(String oldIndexName, String newIndexName, String alias);

    /**
     * 备份索引到指定路径（snapshot）。
     */
    boolean backup(String indexName, String targetPath);

    /**
     * 从备份恢复索引。
     */
    boolean restore(String sourcePath, String indexName);

    // ==================== 批量异步 — 反应式事件流 ====================

    /**
     * 批量添加（异步 + 实时进度事件流）。
     * <p>
     * 适用场景：上传 1 万张图片 / 文档，需要 UI 实时显示每文件进度。
     * 详细协议见 {@link BatchEvent}。
     */
    Flux<BatchEvent> addBatch(String indexName, Flux<VectorRecord> records);

    /**
     * 批量添加（List 入参的便利重载）。
     */
    Flux<BatchEvent> addBatch(String indexName, List<VectorRecord> records);

    /**
     * 批量更新（异步 + 实时进度事件流）。
     */
    Flux<BatchEvent> updateBatch(String indexName, Flux<VectorRecord> records);

    /**
     * 批量删除（异步 + 实时进度事件流）。
     */
    Flux<BatchEvent> deleteBatch(String indexName, Flux<String> ids);
}