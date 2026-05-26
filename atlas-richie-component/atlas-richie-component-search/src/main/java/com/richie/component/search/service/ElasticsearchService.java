package com.richie.component.search.service;

import com.richie.component.search.model.*;
import com.richie.component.search.model.*;

import java.util.List;
import java.util.Optional;

/**
 * 搜索服务接口
 *
 * <p>封装多种搜索引擎的常用操作，统一业务层调用方式，屏蔽底层实现细节。
 * 支持索引管理、单条/批量文档操作、分页/条件/聚合查询等。
 *
 * <p>主要功能：
 * <ul>
 *   <li>索引/集合管理（创建、删除、存在性检查）</li>
 *   <li>文档操作（保存、删除、查询）</li>
 *   <li>搜索查询（分页、条件、聚合、高亮、建议）</li>
 *   <li>批量操作支持</li>
 *   <li>高级查询（布尔查询、嵌套查询、脚本查询）</li>
 *   <li>聚合分析（统计、分组、管道聚合）</li>
 * </ul>
 *
 * <p>支持的搜索引擎：
 * <ul>
 *   <li>Elasticsearch：支持所有功能</li>
 *   <li>Solr：支持基础功能，部分高级功能可能受限</li>
 *   <li>其他搜索引擎：根据具体实现支持相应功能</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * // 基础查询
 * PageResult<User> result = searchService.search(
 *     SearchQueryWrapper.create(User.class, "users")
 *         .eq(User::getName, "张三")
 *         .page(0, 10)
 * );
 *
 * // 高级查询（嵌套查询）
 * PageResult<Product> result = searchService.search(
 *     SearchQueryWrapper.create(Product.class, "products")
 *         .eq(Product::getStatus, "active")
 *         .nested(Product::getComments, nestedBuilder ->
 *             nestedBuilder.eq(Comment::getRating, 5)
 *         )
 * );
 *
 * // 聚合查询
 * Map<String, Object> aggResult = searchService.aggregate(
 *     SearchQueryWrapper.create(Order.class, "orders")
 *         .count(Order::getStatus)
 *         .sum(Order::getAmount)
 * );
 * }</pre>
 *
 * @author richie696
 * @version 2.0
 * @since 2025-08-01
 * @see SearchQuery
 * @see SearchQueryWrapper
 * @see PageResult
 */
public interface ElasticsearchService {

    // ==================== 索引/集合管理 ====================

    /**
     * 创建索引（Elasticsearch）或集合（SolrCloud）
     *
     * <p>根据搜索引擎类型创建相应的索引或集合。
     * <ul>
     *   <li>Elasticsearch：创建索引，使用 mappingJson 作为映射定义</li>
     *   <li>Solr：创建集合，mappingJson 参数可忽略</li>
     * </ul>
     *
     * @param indexName 索引/集合名称
     * @param mappingJson mapping或schema定义（ES为JSON，Solr可忽略）
     * @return 创建成功返回true，已存在或不支持返回false
     */
    boolean createIndex(String indexName, String mappingJson);

    /**
     * 删除索引（Elasticsearch）或集合（SolrCloud）
     *
     * <p>根据搜索引擎类型删除相应的索引或集合。
     *
     * @param indexName 索引/集合名称
     * @return 删除成功返回true，不存在或不支持返回false
     */
    boolean deleteIndex(String indexName);

    /**
     * 判断索引/集合是否存在
     *
     * <p>检查指定的索引或集合是否已存在。
     *
     * @param indexName 索引/集合名称
     * @return 存在返回true，否则false
     */
    boolean indexExists(String indexName);

    // ==================== 文档操作 ====================

    /**
     * 保存单条文档
     *
     * <p>将文档保存到指定的索引或集合中。如果文档已存在，则更新文档。
     *
     * @param indexName 索引/集合名称
     * @param document 文档对象
     * @return 保存后的文档对象（带ID）
     * @param <T> 文档类型
     */
    <T> T save(String indexName, T document);

    /**
     * 根据ID删除单条文档
     *
     * <p>根据文档ID删除指定的文档。
     *
     * @param indexName 索引/集合名称
     * @param docId 文档ID
     * @return 删除成功返回true，否则false
     */
    boolean deleteByDocId(String indexName, String docId);

    /**
     * 根据ID查询单条文档
     *
     * <p>根据文档ID查询指定的文档。
     *
     * @param indexName 索引/集合名称
     * @param docId 文档ID
     * @param clazz 文档类型Class
     * @return Optional包装的文档对象
     * @param <T> 文档类型
     */
    <T> Optional<T> findByDocId(String indexName, String docId, Class<T> clazz);

    /**
     * 批量保存文档
     *
     * <p>批量保存多个文档到指定的索引或集合中。
     *
     * @param indexName 索引/集合名称
     * @param documents 文档列表
     * @return 保存后的文档列表
     * @param <T> 文档类型
     */
    <T> List<T> saveBatch(String indexName, List<T> documents);

    /**
     * 批量根据ID删除文档
     *
     * <p>根据文档ID列表批量删除文档。
     *
     * @param indexName 索引/集合名称
     * @param docIds 文档ID列表
     * @return 删除成功返回true，否则false
     */
    boolean deleteBatchByDocIds(String indexName, List<String> docIds);

    // ==================== 基础查询 ====================

    /**
     * 根据查询条件查找单个文档
     *
     * <p>推荐直接传入 SearchQueryWrapper，内部自动 build。
     *
     * @param wrapper 查询参数构建器
     * @return 匹配的文档对象，如果未找到返回null
     * @param <T> 文档类型
     */
    <T> T findOne(SearchQueryWrapper<T> wrapper);

    /**
     * 根据查询条件删除文档
     *
     * <p>根据指定的查询条件删除匹配的所有文档。
     * <p>注意：此操作会删除所有匹配查询条件的文档，请谨慎使用。
     *
     * @param wrapper 查询参数构建器
     * @return 删除的文档数量
     * @param <T> 文档类型
     */
    <T> long deleteByCondition(SearchQueryWrapper<T> wrapper);

    /**
     * 查询文档总数
     *
     * <p>根据查询条件统计匹配的文档总数。
     *
     * @param wrapper 查询参数构建器
     * @return 匹配文档总数
     * @param <T> 文档类型
     */
    <T> long count(SearchQueryWrapper<T> wrapper);

    // ==================== 高级查询 ====================

    /**
     * 高级查询（支持常规查询、布尔查询、嵌套查询、脚本查询等）
     *
     * <p>提供比基础查询更强大的查询能力：
     * <ul>
     *   <li>布尔查询：must、should、mustNot、filter</li>
     *   <li>嵌套查询：支持嵌套文档查询</li>
     *   <li>脚本查询：支持自定义脚本逻辑</li>
     *   <li>父子查询：支持文档间关系查询</li>
     * </ul>
     *
     * <p>注意：某些高级功能在不同搜索引擎中的支持程度可能不同。
     *
     * @param wrapper 查询参数构建器
     * @return 分页结果PageResult
     * @param <T> 文档类型
     */
    <T> PageResult<T> search(SearchQueryWrapper<T> wrapper);

    // ==================== 原生查询 ====================

    /**
     * 原生查询（使用搜索引擎原生DSL语法）
     *
     * <p>当内置查询构建器无法满足需求时，可以使用原生查询。
     * <p>注意：原生查询在不同搜索引擎中的语法不同，需要谨慎使用。
     *
     * @param wrapper 查询参数构建器
     * @return 查询结果
     * @param <T> 文档类型
     */
    <T> PageResult<T> dslSearch(StringQueryWrapper<T> wrapper);

    /**
     * 原生查询（使用搜索引擎原生API语法）
     *
     * <p>当内置查询构建器无法满足需求时，可以使用原生查询。
     * <p>注意：原生查询在不同搜索引擎中的语法不同，需要谨慎使用。
     *
     * @param wrapper 查询参数构建器
     * @return 查询结果
     * @param <T> 文档类型
     */
    <T> PageResult<T> nativeSearch(NativeQueryWrapper<T> wrapper);

}
