package com.richie.component.search.model;

import com.richie.component.search.enums.QueryType;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

/**
 * 通用搜索查询参数对象
 *
 * <p>支持多搜索引擎的统一查询参数封装，提供链式调用方式。
 * 支持条件查询、分页、排序、聚合、原生查询、高亮、建议等功能。
 *
 * <p>主要功能：
 * <ul>
 *   <li>条件查询：支持泛型条件对象</li>
 *   <li>分页查询：支持页码和页面大小设置</li>
 *   <li>排序：支持多字段排序</li>
 *   <li>聚合查询：支持多种聚合类型</li>
 *   <li>过滤条件：支持多条件过滤</li>
 *   <li>原生查询：支持搜索引擎原生语法</li>
 *   <li>高亮查询：支持字段高亮</li>
 *   <li>建议查询：支持搜索建议</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>
 * // 基础查询
 * SearchQuery&lt;UserQuery&gt; query = new SearchQuery&lt;&gt;()
 *     .setIndexOrCollection("users")
 *     .setConditions(new UserQuery().setName("张三"))
 *     .setPage(0)
 *     .setSize(10);
 *
 * // 带排序的查询
 * SearchQuery&lt;ProductQuery&gt; query = new SearchQuery&lt;&gt;()
 *     .setIndexOrCollection("products")
 *     .setConditions(new ProductQuery().setCategory("电子产品"))
 *     .setSort(Map.of("price", "desc", "createTime", "asc"));
 *
 * // 聚合查询
 * Map&lt;String, Object&gt; aggregations = Map.of(
 *     "category_stats", Map.of("type", "terms", "field", "category"),
 *     "price_stats", Map.of("type", "stats", "field", "price")
 * );
 * SearchQuery&lt;ProductQuery&gt; query = new SearchQuery&lt;&gt;()
 *     .setIndexOrCollection("products")
 *     .setAggregations(aggregations);
 * </pre>
 *
 * @param <T> 查询条件对象类型
 * @author richie696
 * @version 1.0
 * @since 2025-08-12
 * @see QueryCondition
 * @see PageResult
 */
@Data
@Accessors(chain = true)
public class SearchQuery<T> {

    private QueryType type;

    /**
     * 索引/集合名称
     *
     * <p>指定要查询的索引（Elasticsearch）或集合（Solr）名称。
     * <p>示例：users、products、orders
     */
    private String indexOrCollection;

    private Class<T> entityClass;

    /**
     * 查询条件对象
     *
     * <p>支持泛型的查询条件对象，可以是任意类型的条件封装。
     * <p>示例：UserQuery、ProductQuery 等自定义查询条件类
     */
    private T entity;

    private List<QueryCondition> conditions;

    /**
     * 分页页码
     *
     * <p>分页查询的页码，从 0 开始计数。
     * <p>示例：0 表示第一页，1 表示第二页
     */
    private int page;

    /**
     * 分页大小
     *
     * <p>每页返回的文档数量。
     * <p>示例：10、20、50
     */
    private int size;

    /**
     * 排序字段及顺序
     *
     * <p>key 为字段名，value 为排序方向（asc/desc）。
     * <p>示例：{"price": "desc", "createTime": "asc"}
     */
    private Map<String, String> sort;

    /**
     * 聚合参数
     *
     * <p>key 为聚合名，value 为聚合类型及字段等配置。
     * <p>示例：{"groupByStatus": {"type": "terms", "field": "status"}}
     * <p>支持的聚合类型：
     * <ul>
     *   <li>terms：分组聚合</li>
     *   <li>stats：统计聚合</li>
     *   <li>avg：平均值聚合</li>
     *   <li>sum：求和聚合</li>
     *   <li>count：计数聚合</li>
     * </ul>
     */
    private Map<String, Object> aggregations;

    /**
     * 过滤条件
     *
     * <p>支持多条件过滤，每个过滤条件为一个 Map。
     * <p>示例：[{"field": "status", "value": "active"}, {"field": "price", "range": [100, 1000]}]
     */
    private List<Map<String, Object>> filters;

    /**
     * 原生查询
     *
     * <p>支持搜索引擎原生语法，如 ES 的 JSON、Solr 的 q 语法等。
     * <p>示例：
     * <ul>
     *   <li>Elasticsearch: {"query": {"match": {"name": "张三"}}}</li>
     *   <li>Solr: name:张三 AND age:[20 TO 30]</li>
     * </ul>
     */
    private String stringQuery;

    private Query nativeQuery;

    /**
     * 高亮字段列表
     *
     * <p>指定需要高亮显示的字段名列表。
     * <p>示例：["name", "description"]
     */
    public List<String> highlightFields;

    /**
     * 建议查询参数
     *
     * <p>用于搜索建议和自动补全功能的参数配置。
     * <p>示例：{"suggest": {"field": "name", "size": 5}}
     */
    private Map<String, Object> suggestions;

    /**
     * 分页查询中获取精确的总数
     */
    private Boolean trackTotalHits;

}
