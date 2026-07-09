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
import com.richie.component.vector.model.VectorDocument;
import com.richie.component.vector.model.VectorQuery;
import com.richie.component.vector.model.VectorSearchResult;

import java.util.List;

/**
 * 向量服务接口
 * 提供统一的向量数据库操作能力
 * 屏蔽不同向量数据库的差异，提供一致的API
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public interface VectorService {

    /**
     * 添加单个文档
     *
     * @param document 向量文档
     * @return 文档ID
     */
    String addDocument(VectorDocument document);

    /**
     * 批量添加文档
     *
     * @param documents 向量文档列表
     * @return 文档ID列表
     */
    List<String> addDocuments(List<VectorDocument> documents);

    /**
     * 更新文档
     *
     * @param id       文档ID
     * @param document 更新的文档内容
     */
    void updateDocument(String id, VectorDocument document);

    /**
     * 删除单个文档
     *
     * @param id 文档ID
     */
    void deleteDocument(String id);

    /**
     * 批量删除文档
     *
     * @param ids 文档ID列表
     */
    void deleteDocuments(List<String> ids);

    /**
     * 获取文档
     *
     * @param id 文档ID
     * @return 向量文档
     */
    VectorDocument getDocument(String id);

    /**
     * 批量获取文档
     *
     * @param ids 文档ID列表
     * @return 向量文档列表
     */
    List<VectorDocument> getDocuments(List<String> ids);

    /**
     * 向量搜索
     *
     * @param query 查询条件
     * @return 搜索结果列表
     */
    List<VectorSearchResult> search(VectorQuery query);

    /**
     * 基于向量进行搜索
     *
     * @param indexName 索引名称
     * @param vector 查询向量
     * @param limit  返回结果数量限制
     * @return 搜索结果列表
     */
    List<VectorSearchResult> searchByVector(String indexName, float[] vector, int limit);

    /**
     * 基于文本进行搜索
     *
     * @param text  查询文本
     * @param limit 返回结果数量限制
     * @return 搜索结果列表
     */
    List<VectorSearchResult> searchByText(String text, int limit);

    /**
     * 基于向量和阈值进行搜索
     *
     * @param indexName 索引名称
     * @param vector   查询向量
     * @param limit    返回结果数量限制
     * @param minScore 最小相似度阈值
     * @return 搜索结果列表
     */
    List<VectorSearchResult> searchByVector(String indexName, float[] vector, int limit, double minScore);

    /**
     * 基于文本和阈值进行搜索
     *
     * @param text     查询文本
     * @param limit    返回结果数量限制
     * @param minScore 最小相似度阈值
     * @return 搜索结果列表
     */
    List<VectorSearchResult> searchByText(String text, int limit, double minScore);

    /**
     * 创建索引/集合/表
     * @param indexName 索引名
     * @param config    索引配置
     */
    void createIndex(String indexName, VectorProperties.IndexConfig config);

    /**
     * 删除索引/集合/表
     * @param indexName 索引名
     */
    void deleteIndex(String indexName);

    /**
     * 判断索引/集合/表是否存在
     * @param indexName 索引名
     * @return 是否存在
     */
    boolean indexExists(String indexName);

    /**
     * 获取索引/集合/表信息
     * @param indexName 索引名
     * @return 索引配置信息
     */
    VectorProperties.IndexConfig getIndexConfig(String indexName);

    /**
     * 统计文档数量
     * @param indexName 索引名
     * @return 文档数量
     */
    long countDocuments(String indexName);

    /**
     * 分页查询文档
     * @param indexName 索引名
     * @param offset    起始位置
     * @param limit     返回数量
     * @return 文档列表
     */
    java.util.List<VectorDocument> listDocuments(String indexName, int offset, int limit);

}
