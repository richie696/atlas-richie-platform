package com.richie.component.vector.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

/**
 * 向量查询模型
 * 用于定义向量搜索的查询参数和条件
 * 这个类就像是搜索的"查询单"，包含了所有搜索条件和参数
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
@Data
@Accessors(chain = true)
public class VectorQuery {

    /**
     * 查询向量
     * 用于相似度搜索的向量数据
     * 向量维度必须与索引配置一致
     * <p>
     * 示例：
     * - [0.1, 0.2, 0.3, ..., 0.1536] (1536维向量)
     * - [0.5, -0.1, 0.8, ..., 0.768] (768维向量)
     * <p>
     * 注意：
     * - 向量值通常在-1到1之间
     * - 不同嵌入模型生成的向量维度不同
     * - 向量质量直接影响搜索结果
     */
    private float[] vector;

    /**
     * 查询文本
     * 用于文本搜索的查询字符串
     * 系统会自动将文本转换为向量进行搜索
     * <p>
     * 示例：
     * - "Java编程教程"
     * - "Spring Boot快速入门"
     * - "用户认证和授权"
     * <p>
     * 注意：
     * - 如果同时提供vector和text，优先使用vector
     * - 文本质量直接影响搜索结果
     * - 支持多语言文本
     */
    private String text;

    /**
     * 返回结果数量限制
     * 限制返回的相似文档数量
     * <p>
     * 建议值：
     * - 5-10：精确匹配
     * - 10-50：一般搜索
     * - 50-100：广泛搜索
     * - >100：大规模检索
     * <p>
     * 注意：
     * - 数值越大，性能消耗越高
     * - 建议根据实际需求设置合理值
     */
    private Integer limit;

    /**
     * 相似度阈值
     * 最小相似度分数，低于此值的文档不会返回
     * 取值范围：0.0 - 1.0
     * <p>
     * 建议值：
     * - 0.8-0.9：高精度匹配
     * - 0.6-0.8：一般匹配
     * - 0.4-0.6：宽松匹配
     * - <0.4：非常宽松
     * <p>
     * 用途：
     * - 过滤低质量结果
     * - 提高搜索精度
     * - 控制结果数量
     */
    private Double minScore;

    /**
     * 文档类型过滤
     * 只搜索指定类型的文档
     * <p>
     * 示例：
     * - "article"：只搜索文章
     * - "product"：只搜索产品
     * - "user_profile"：只搜索用户档案
     * <p>
     * 用途：
     * - 业务场景隔离
     * - 提高搜索精度
     * - 减少无关结果
     */
    private String type;

    /**
     * 标签过滤
     * 只搜索包含指定标签的文档
     * <p>
     * 示例：
     * - ["java", "tutorial"]：包含java和tutorial标签
     * - ["spring-boot"]：包含spring-boot标签
     * <p>
     * 逻辑：
     * - 所有标签都必须匹配（AND逻辑）
     * - 大小写敏感
     * - 支持部分匹配
     */
    private List<String> tags;

    /**
     * 命名空间过滤
     * 只搜索指定命名空间的文档
     * <p>
     * 示例：
     * - "user_profiles"：用户档案命名空间
     * - "product_catalog"：产品目录命名空间
     * - "knowledge_base"：知识库命名空间
     * <p>
     * 用途：
     * - 多租户隔离
     * - 业务场景分离
     * - 数据管理
     */
    private String namespace;

    /**
     * 状态过滤
     * 只搜索指定状态的文档
     * <p>
     * 示例：
     * - "active"：只搜索活跃文档
     * - ["active", "pending"]：搜索活跃和待审核文档
     * <p>
     * 常用状态：
     * - "active"：活跃状态
     * - "inactive"：非活跃状态
     * - "pending"：待审核
     * - "archived"：已归档
     */
    private List<String> status;

    /**
     * 元数据过滤
     * 基于文档元数据进行过滤
     * <p>
     * 示例：
     * {
     * "author": "张三",
     * "category": "技术文档",
     * "language": "zh-CN",
     * "rating": {"$gte": 4.0}
     * }
     * <p>
     * 支持的操作符：
     * - "$eq"：等于
     * - "$ne"：不等于
     * - "$gt"：大于
     * - "$gte"：大于等于
     * - "$lt"：小于
     * - "$lte"：小于等于
     * - "$in"：在列表中
     * - "$nin"：不在列表中
     */
    private Map<String, Object> metadataFilter;

    /**
     * 时间范围过滤
     * 基于创建时间进行过滤
     */
    private TimeRange timeRange;

    /**
     * 排序方式
     * 定义搜索结果的排序规则
     * <p>
     * 可选值：
     * - "score"：按相似度分数排序（默认）
     * - "created_at"：按创建时间排序
     * - "updated_at"：按更新时间排序
     * - "score_desc"：按相似度分数降序
     * - "score_asc"：按相似度分数升序
     */
    private String sortBy;

    /**
     * 是否包含向量数据
     * 控制返回结果是否包含向量数据
     * <p>
     * 注意：
     * - 向量数据较大，影响网络传输
     * - 通常只需要相似度分数，不需要原始向量
     * - 特殊场景下可能需要向量数据进行进一步处理
     */
    private boolean includeVector;

    /**
     * 是否包含元数据
     * 控制返回结果是否包含元数据
     * <p>
     * 注意：
     * - 元数据可能包含敏感信息
     * - 根据业务需求决定是否返回
     * - 影响网络传输大小
     */
    private boolean includeMetadata;

    /**
     * 时间范围过滤类
     * 用于定义时间范围过滤条件
     */
    @Data
    @Accessors(chain = true)
    public static class TimeRange {

        /**
         * 开始时间
         * 搜索创建时间在此时间之后的文档
         */
        private String startTime;

        /**
         * 结束时间
         * 搜索创建时间在此时间之前的文档
         */
        private String endTime;

        /**
         * 时间字段
         * 指定使用哪个时间字段进行过滤
         * <p>
         * 可选值：
         * - "created_at"：创建时间（默认）
         * - "updated_at"：更新时间
         */
        private String field = "created_at";
    }

    /**
     * 创建向量查询
     * <p>
     * 静态工厂方法，用于快速创建向量查询
     *
     * @param vector 查询向量
     * @param limit  返回结果数量限制
     * @return 配置好的向量查询对象
     */
    public static VectorQuery of(float[] vector, Integer limit) {
        return new VectorQuery()
                .setVector(vector)
                .setLimit(limit)
                .setSortBy("score")
                .setIncludeVector(false)
                .setIncludeMetadata(true);
    }

    /**
     * 创建文本查询
     *
     * @param text  查询文本
     * @param limit 返回结果数量限制
     * @return 配置好的向量查询对象
     */
    public static VectorQuery of(String text, Integer limit) {
        return new VectorQuery()
                .setText(text)
                .setLimit(limit)
                .setSortBy("score")
                .setIncludeVector(false)
                .setIncludeMetadata(true);
    }

    /**
     * 创建带阈值的向量查询
     *
     * @param vector   查询向量
     * @param limit    返回结果数量限制
     * @param minScore 最小相似度阈值
     * @return 配置好的向量查询对象
     */
    public static VectorQuery of(float[] vector, Integer limit, Double minScore) {
        return of(vector, limit).setMinScore(minScore);
    }

    /**
     * 创建带类型的查询
     *
     * @param vector 查询向量
     * @param limit  返回结果数量限制
     * @param type   文档类型
     * @return 配置好的向量查询对象
     */
    public static VectorQuery of(float[] vector, Integer limit, String type) {
        return of(vector, limit).setType(type);
    }

    /**
     * 创建带标签的查询
     *
     * @param vector 查询向量
     * @param limit  返回结果数量限制
     * @param tags   标签列表
     * @return 配置好的向量查询对象
     */
    public static VectorQuery of(float[] vector, Integer limit, List<String> tags) {
        return of(vector, limit).setTags(tags);
    }
}
