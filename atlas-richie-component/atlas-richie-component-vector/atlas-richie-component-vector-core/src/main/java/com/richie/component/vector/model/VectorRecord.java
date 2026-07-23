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

import lombok.Data;
import lombok.experimental.Accessors;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 统一向量记录（替代 v1 的 {@code VectorDocument}）。
 * <p>
 * 多模态感知：通过 {@link VectorContent} 表达文本/图片两种模态。
 * 字段语义：
 * <ul>
 *   <li>{@link #id} — 记录唯一标识；为 {@code null} 时系统自动生成 UUID</li>
 *   <li>{@link #indexName} — 目标索引（必填）</li>
 *   <li>{@link #content} — 多模态内容（必填）</li>
 *   <li>{@link #metadata} — 业务元数据；保留键 {@code __itemId} 用于批量事件 ItemId</li>
 * </ul>
 *
 * @author richie696
 * @since 2.0.0
 */
@Data
@Accessors(chain = true)
public class VectorRecord {

    /**
     * 元数据保留键：批量事件中的 ItemId。
     */
    public static final String META_ITEM_ID = "__itemId";

    /**
     * 文档唯一标识符
     */
    private String id;

    /**
     * 目标索引名称
     */
    private String indexName;

    /**
     * 多模态内容（文本或图片）
     */
    private VectorContent content;

    /**
     * 自定义元数据
     */
    private Map<String, Object> metadata;

    /**
     * 文档标签
     */
    private String[] tags;

    /**
     * 文档来源
     */
    private String source;

    /**
     * 命名空间（多租户隔离）
     */
    private String namespace;

    /**
     * 文档状态（active / inactive / deleted / pending / archived）
     */
    private String status;

    /**
     * 文档分数（相关性或质量评分）
     */
    private Double score;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    // ==================== 工厂方法（文本模态） ====================

    public static VectorRecord text(String indexName, String text) {
        return new VectorRecord()
                .setIndexName(indexName)
                .setContent(new VectorContent.TextContent(text, "text/plain"))
                .setId(UUID.randomUUID().toString())
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now())
                .setStatus("active");
    }

    public static VectorRecord text(String indexName, String text, Map<String, Object> metadata) {
        return text(indexName, text).setMetadata(metadata);
    }

    public static VectorRecord text(String indexName, String id, String text) {
        return text(indexName, text).setId(id);
    }

    // ==================== 工厂方法（图片模态） ====================

    public static VectorRecord image(String indexName, byte[] data, String mimeType) {
        return new VectorRecord()
                .setIndexName(indexName)
                .setContent(new VectorContent.ImageContent(data, mimeType))
                .setId(UUID.randomUUID().toString())
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now())
                .setStatus("active");
    }

    public static VectorRecord image(String indexName, Path path, String mimeType) {
        try {
            byte[] data = Files.readAllBytes(path);
            return image(indexName, data, mimeType);
        } catch (Exception e) {
            throw new IllegalArgumentException("读取图片文件失败: " + path, e);
        }
    }

    /**
     * 图片 URL 形式 — 用于下载后入库。
     * <p>
     * 将 URL 暂存在 metadata[__imageUrl]，由调用方在入库前转换为 byte[]。
     * 提供该方法的目的是让调用方能先组好批量请求，再统一异步下载。
     */
    public static VectorRecord imageUrl(String indexName, String url, String mimeType) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("__imageUrl", url);
        return new VectorRecord()
                .setIndexName(indexName)
                .setContent(new VectorContent.ImageContent(new byte[0], mimeType))  // 占位，由 pipeline 下载替换
                .setMetadata(meta)
                .setId(UUID.randomUUID().toString())
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now())
                .setStatus("pending");
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取用于批量事件追踪的 ItemId。
     * 优先级：metadata.__itemId > id
     */
    public String itemId() {
        if (metadata != null && metadata.get(META_ITEM_ID) instanceof String s && !s.isBlank()) {
            return s;
        }
        return id;
    }
}