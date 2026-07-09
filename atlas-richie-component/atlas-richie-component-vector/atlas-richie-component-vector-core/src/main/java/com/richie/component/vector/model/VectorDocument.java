/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 向量文档模型
 * 用于存储向量数据的基本数据结构
 * 这个类就像是向量数据库中的"记录"，包含了向量和相关的元数据
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
@Data
@Accessors(chain = true)
public class VectorDocument {

    /**
     * 文档唯一标识符
     * 用于标识和检索特定的向量文档
     * 如果不指定，系统会自动生成
     * <p>
     * 示例：
     * - "doc_001"
     * - "user_profile_123"
     * - "product_description_456"
     */
    private String id;

    /**
     * 文档内容
     * 原始的文本内容，用于生成向量或作为检索的上下文
     * <p>
     * 示例：
     * - "Java是一种面向对象的编程语言"
     * - "Spring Boot是一个快速开发框架"
     * - "用户配置文件信息"
     */
    private String content;

    /**
     * 向量数据
     * 文档内容对应的向量表示，通常是浮点数数组
     * 向量维度取决于使用的嵌入模型
     * <p>
     * 示例：
     * - [0.1, 0.2, 0.3, ..., 0.1536] (1536维向量)
     * - [0.5, -0.1, 0.8, ..., 0.768] (768维向量)
     * <p>
     * 注意：
     * - 向量维度必须与索引配置一致
     * - 向量值通常在-1到1之间
     * - 不同嵌入模型生成的向量维度不同
     */
    private float[] vector;

    /**
     * 文档类型
     * 用于分类和过滤不同类型的文档
     * <p>
     * 示例：
     * - "article"：文章
     * - "product"：产品
     * - "user_profile"：用户档案
     * - "code_snippet"：代码片段
     * - "question"：问题
     * - "answer"：答案
     */
    private String type;

    /**
     * 文档标签
     * 用于标记和分类文档的标签列表
     * <p>
     * 示例：
     * - ["java", "programming", "tutorial"]
     * - ["spring-boot", "framework", "backend"]
     * - ["user", "profile", "personal"]
     * <p>
     * 用途：
     * - 文档分类和过滤
     * - 相似文档聚类
     * - 检索结果优化
     */
    private String[] tags;

    /**
     * 文档来源
     * 记录文档的来源信息
     * <p>
     * 示例：
     * - "user_upload"：用户上传
     * - "api_import"：API导入
     * - "crawler"：爬虫抓取
     * - "manual_input"：手动输入
     * - "system_generated"：系统生成
     */
    private String source;

    /**
     * 创建时间
     * 文档创建的时间戳
     * 用于时间排序和审计
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     * 文档最后修改的时间戳
     * 用于版本控制和变更追踪
     */
    private LocalDateTime updatedAt;

    /**
     * 文档分数
     * 文档的相关性分数或质量评分
     * 用于排序和过滤
     * <p>
     * 示例：
     * - 0.95：高质量文档
     * - 0.75：中等质量文档
     * - 0.50：低质量文档
     * <p>
     * 用途：
     * - 检索结果排序
     * - 质量过滤
     * - 推荐系统
     */
    private Double score;

    /**
     * 文档状态
     * 文档的当前状态
     * <p>
     * 示例：
     * - "active"：活跃状态
     * - "inactive"：非活跃状态
     * - "deleted"：已删除
     * - "pending"：待审核
     * - "archived"：已归档
     */
    private String status;

    /**
     * 自定义元数据
     * 存储额外的文档信息，格式灵活
     * <p>
     * 示例：
     * {
     * "author": "张三",
     * "category": "技术文档",
     * "language": "zh-CN",
     * "word_count": 1500,
     * "read_count": 1000,
     * "rating": 4.5
     * }
     * <p>
     * 用途：
     * - 存储业务相关的额外信息
     * - 支持复杂的过滤和查询
     * - 扩展文档的功能
     */
    private Map<String, Object> metadata;

    /**
     * 命名空间
     * 用于隔离不同业务场景的文档
     * <p>
     * 示例：
     * - "user_profiles"：用户档案
     * - "product_catalog"：产品目录
     * - "knowledge_base"：知识库
     * - "support_tickets"：支持工单
     * <p>
     * 用途：
     * - 多租户隔离
     * - 业务场景分离
     * - 数据管理
     */
    private String namespace;

    /**
     * 创建向量文档
     * 静态工厂方法，用于快速创建向量文档
     *
     * @param content 文档内容
     * @param vector  向量数据
     * @return 配置好的向量文档对象
     */
    public static VectorDocument of(String content, float[] vector) {
        return new VectorDocument()
                .setContent(content)
                .setVector(vector)
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now())
                .setStatus("active");
    }

    /**
     * 创建带ID的向量文档
     *
     * @param id      文档ID
     * @param content 文档内容
     * @param vector  向量数据
     * @return 配置好的向量文档对象
     */
    public static VectorDocument of(String id, String content, float[] vector) {
        return of(content, vector).setId(id);
    }

    /**
     * 创建带类型的向量文档
     *
     * @param content 文档内容
     * @param vector  向量数据
     * @param type    文档类型
     * @return 配置好的向量文档对象
     */
    public static VectorDocument of(String content, float[] vector, String type) {
        return of(content, vector).setType(type);
    }

    /**
     * 创建带标签的向量文档
     *
     * @param content 文档内容
     * @param vector  向量数据
     * @param tags    文档标签
     * @return 配置好的向量文档对象
     */
    public static VectorDocument of(String content, float[] vector, String[] tags) {
        return of(content, vector).setTags(tags);
    }

    /**
     * 创建完整的向量文档
     *
     * @param id      文档ID
     * @param content 文档内容
     * @param vector  向量数据
     * @param type    文档类型
     * @param tags    文档标签
     * @return 配置好的向量文档对象
     */
    public static VectorDocument of(String id, String content, float[] vector, String type, String[] tags) {
        return of(id, content, vector)
                .setType(type)
                .setTags(tags);
    }
}
