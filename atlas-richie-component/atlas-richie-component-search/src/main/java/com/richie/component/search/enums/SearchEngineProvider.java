package com.richie.component.search.enums;

/**
 * 搜索引擎提供者枚举
 *
 * <p>定义支持的搜索引擎类型，用于配置和选择不同的搜索引擎实现。
 *
 * <p>支持的搜索引擎：
 * <ul>
 *   <li>{@link #ELASTICSEARCH} - Elasticsearch 搜索引擎</li>
 *   <li>{@link #SOLR} - Apache Solr 搜索引擎</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>
 * // 在配置文件中指定搜索引擎
 * platform:
 *   component:
 *     search:
 *       provider: ELASTICSEARCH
 *
 * // 在代码中使用
 * SearchEngineProvider provider = SearchEngineProvider.ELASTICSEARCH;
 * </pre>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-27
 */
public enum SearchEngineProvider {

    NONE,

    /**
     * Elasticsearch 搜索引擎
     *
     * <p>基于 Lucene 的分布式搜索引擎，提供强大的全文搜索和分析功能。
     * <p>特点：
     * <ul>
     *   <li>分布式架构，支持水平扩展</li>
     *   <li>强大的聚合和分析功能</li>
     *   <li>支持复杂的查询语法</li>
     *   <li>实时搜索和近实时搜索</li>
     * </ul>
     */
    ELASTICSEARCH,

    /**
     * Apache Solr 搜索引擎
     *
     * <p>基于 Lucene 的企业级搜索平台，提供丰富的搜索功能。
     * <p>特点：
     * <ul>
     *   <li>基于 HTTP 的 RESTful API</li>
     *   <li>支持多种数据格式（XML、JSON、CSV等）</li>
     *   <li>强大的分面搜索功能</li>
     *   <li>支持多种查询语法</li>
     * </ul>
     */
    SOLR
}
