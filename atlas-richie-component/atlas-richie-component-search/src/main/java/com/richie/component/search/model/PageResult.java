package com.richie.component.search.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 通用分页结果对象
 *
 * <p>支持多搜索引擎的统一分页结果封装，提供标准的分页信息。
 * 支持数据内容、分页信息、聚合结果、高亮内容、建议结果等。
 *
 * <p>主要功能：
 * <ul>
 *   <li>分页数据：当前页的数据列表</li>
 *   <li>分页信息：总条数、当前页码、每页大小</li>
 *   <li>聚合结果：查询的聚合统计信息</li>
 *   <li>高亮内容：匹配字段的高亮显示</li>
 *   <li>建议结果：搜索建议和自动补全结果</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>
 * // 创建分页结果
 * PageResult&lt;User&gt; result = PageResult.&lt;User&gt;builder()
 *     .content(userList)
 *     .total(1000)
 *     .page(0)
 *     .size(10)
 *     .build();
 *
 * // 带聚合结果的分页
 * PageResult&lt;Product&gt; result = PageResult.&lt;Product&gt;builder()
 *     .content(productList)
 *     .total(500)
 *     .page(0)
 *     .size(20)
 *     .aggregations(aggregationMap)
 *     .build();
 *
 * // 带高亮的分页结果
 * PageResult&lt;Document&gt; result = PageResult.&lt;Document&gt;builder()
 *     .content(documentList)
 *     .total(200)
 *     .page(0)
 *     .size(10)
 *     .highlights(highlightMap)
 *     .build();
 * </pre>
 *
 * @param <T> 结果数据类型
 * @author richie696
 * @version 1.0
 * @since 2025-08-12
 * @see SearchQuery
 */
@Data
@Builder
public class PageResult<T> {

    /**
     * 当前页数据
     *
     * <p>当前页返回的文档列表。
     * <p>示例：用户列表、产品列表等
     */
    private List<T> content;

    /**
     * 总条数
     *
     * <p>符合查询条件的文档总数。
     * <p>用于计算总页数和分页导航。
     */
    private long total;

    /**
     * 当前页码
     *
     * <p>当前页的页码，从 0 开始计数。
     * <p>示例：0 表示第一页，1 表示第二页
     */
    private int page;

    /**
     * 每页大小
     *
     * <p>每页返回的文档数量。
     * <p>示例：10、20、50
     */
    private int size;

    /**
     * 聚合结果
     *
     * <p>查询的聚合统计信息，key 为聚合名称，value 为聚合结果。
     * <p>示例：{"category_stats": {"buckets": [...]}, "price_stats": {"avg": 100.5}}
     */
    private Map<String, Object> aggregations;

    /**
     * 高亮内容
     *
     * <p>key 为文档ID，value 为高亮字段的 Map。
     * <p>高亮字段 Map 的 key 为字段名，value 为高亮片段列表。
     * <p>示例：{"doc1": {"name": ["&lt;em&gt;张三&lt;/em&gt;"], "description": ["包含&lt;em&gt;关键词&lt;/em&gt;的文本"]}}
     */
    private Map<String, Map<String, List<String>>> highlights;

    /**
     * 建议/自动补全结果
     *
     * <p>key 为建议类型，value 为建议列表。
     * <p>示例：{"name_suggest": ["张三", "张四"], "category_suggest": ["电子产品", "服装"]}
     */
    private Map<String, List<String>> suggestions;

}
