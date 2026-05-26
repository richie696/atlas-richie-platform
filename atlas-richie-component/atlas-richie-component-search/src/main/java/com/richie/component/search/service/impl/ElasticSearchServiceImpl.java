package com.richie.component.search.service.impl;

import com.richie.component.search.model.*;
import com.richie.component.search.model.*;
import com.richie.component.search.service.ElasticsearchService;
import com.richie.component.search.util.ConditionUtils;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.*;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Elasticsearch 搜索服务实现类
 *
 * <p>基于 Spring Data Elasticsearch 实现搜索服务，提供完整的 Elasticsearch 搜索功能。
 * 支持索引管理、文档操作、查询搜索、聚合分析等。
 *
 * <p>主要功能：
 * <ul>
 *   <li>索引管理：创建、删除、存在性检查</li>
 *   <li>文档操作：保存、删除、查询</li>
 *   <li>搜索查询：分页、条件、聚合、高亮、建议</li>
 *   <li>批量操作：批量保存、批量删除</li>
 *   <li>原生查询：支持 Elasticsearch 原生 JSON 查询</li>
 * </ul>
 *
 * <p>配置条件：
 * <p>当 {@code platform.component.search.provider=elasticsearch} 时启用。
 *
 * <p>依赖组件：
 * <ul>
 *   <li>ElasticsearchTemplate：Spring Data Elasticsearch 模板</li>
 *   <li>ElasticsearchClient：Elasticsearch 客户端</li>
 *   <li>QueryConditionAdapter：查询条件适配器</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @see ElasticsearchService
 * @see ElasticsearchTemplate
 * @since 2025-07-27
 */
@Slf4j
@SuppressWarnings("unchecked")
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "platform.component.search", name = "provider", havingValue = "elasticsearch")
public class ElasticSearchServiceImpl implements ElasticsearchService {

    private final ElasticsearchTemplate template;
    private final ElasticsearchClient elasticsearchClient;

    @Override
    public boolean createIndex(String indexName, String mappingJson) {
        IndexOperations indexOps = template.indexOps(IndexCoordinates.of(indexName));
        if (!indexOps.exists()) {
            indexOps.create();
            if (mappingJson != null && !mappingJson.isEmpty()) {
                indexOps.putMapping(Document.parse(mappingJson));
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean deleteIndex(String indexName) {
        IndexOperations indexOps = template.indexOps(IndexCoordinates.of(indexName));
        if (indexOps.exists()) {
            return indexOps.delete();
        }
        return false;
    }

    @Override
    public boolean indexExists(String indexName) {
        return template.indexOps(IndexCoordinates.of(indexName)).exists();
    }

    @Override
    public <T> T save(String indexName, T document) {
        try {
            log.debug("开始保存文档到索引: {}", indexName);
            T result = template.save(document, IndexCoordinates.of(indexName));
            log.debug("文档保存成功到索引: {}", indexName);
            return result;
        } catch (Exception e) {
            log.error("保存文档到索引 {} 失败: {}", indexName, e.getMessage(), e);

            // 如果是 IK 分词器相关错误，记录详细信息
            if (e.getMessage() != null && e.getMessage().contains("analyzer.dic.Dictionary")) {
                log.error("检测到 IK 分词器词典初始化问题，建议检查:");
                log.error("1. IK 分词器插件是否正确安装");
                log.error("2. Elasticsearch 节点是否需要重启");
                log.error("3. 索引映射中的分析器配置是否正确");
            }

            throw e;
        }
    }

    @Override
    public boolean deleteByDocId(String indexName, String docId) {
        template.delete(docId, IndexCoordinates.of(indexName));
        return true;
    }

    @Override
    public <T> Optional<T> findByDocId(String indexName, String docId, Class<T> clazz) {
        T entity = template.get(docId, clazz, IndexCoordinates.of(indexName));
        return Optional.ofNullable(entity);
    }

    @Override
    public <T> List<T> saveBatch(String indexName, List<T> documents) {
        try {
            log.debug("开始批量保存 {} 个文档到索引: {}", documents.size(), indexName);
            Iterable<T> saved = template.save(documents, IndexCoordinates.of(indexName));
            log.debug("批量保存成功到索引: {}", indexName);
            return (List<T>) saved;
        } catch (Exception e) {
            log.error("批量保存文档到索引 {} 失败: {}", indexName, e.getMessage(), e);

            // 如果是 IK 分词器相关错误，记录详细信息
            if (e.getMessage() != null && e.getMessage().contains("analyzer.dic.Dictionary")) {
                log.error("检测到 IK 分词器词典初始化问题，建议检查:");
                log.error("1. IK 分词器插件是否正确安装");
                log.error("2. Elasticsearch 节点是否需要重启");
                log.error("3. 索引映射中的分析器配置是否正确");
            }

            throw e;
        }
    }

    @Override
    public boolean deleteBatchByDocIds(String indexName, List<String> docIds) {
        try {
            log.debug("开始批量删除 {} 个文档从索引: {}", docIds.size(), indexName);
            Query query = template.idsQuery(docIds);
            template.delete(query, IndexCoordinates.of(indexName));
            log.debug("批量删除成功从索引: {}", indexName);
            return true;
        } catch (Exception e) {
            log.error("批量删除文档从索引 {} 失败: {}", indexName, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public <T> long deleteByCondition(SearchQueryWrapper<T> wrapper) {
        try {
            SearchQuery<T> searchQuery = wrapper.build();
            String indexName = searchQuery.getIndexOrCollection();

            log.debug("开始根据 SearchQueryWrapper 删除文档从索引: {}", indexName);

            // 使用新的查询构建器
            Query esQuery = buildElasticsearchQuery(wrapper);

            // 先统计要删除的文档数量
            long countToDelete = template.count(esQuery, IndexCoordinates.of(indexName));
            log.debug("准备删除 {} 个匹配的文档", countToDelete);

            // 执行删除操作
            DeleteQuery deleteQuery = DeleteQuery.builder(esQuery).build();
            ByQueryResponse response = template.delete(deleteQuery, searchQuery.getEntityClass(), IndexCoordinates.of(indexName));

            log.info("成功根据 SearchQueryWrapper 删除 {} 个文档从索引: {}", response.getDeleted(), indexName);
            return countToDelete;

        } catch (Exception e) {
            log.error("根据 SearchQueryWrapper 删除文档失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public <T> long count(SearchQueryWrapper<T> wrapper) {
        try {
            SearchQuery<T> searchQuery = wrapper.build();
            String indexName = searchQuery.getIndexOrCollection();

            if (searchQuery.getConditions().isEmpty()) {
                log.debug("当前是无条件统计全部 {} 的文档数量", indexName);
                return fastCount(indexName);
            }

            log.debug("从索引开始根据 SearchQueryWrapper 统计 {} 文档数量", indexName);

            // 构建查询条件
            Query esQuery = buildElasticsearchQuery(wrapper);

            esQuery = optimizeCountQuery(esQuery);

            // 执行计数查询
            long count = template.count(esQuery, IndexCoordinates.of(indexName));

            log.debug("成功统计到 {} 个文档从索引: {}", count, indexName);

            return count;

        } catch (Exception e) {
            log.error("根据 SearchQueryWrapper 统计文档数量失败: {}", e.getMessage(), e);
            throw new RuntimeException("统计文档数量失败", e);
        }
    }

    /**
     * 优化简单计数查询
     *
     * <p>对于简单查询，可以禁用一些不必要的功能来提高计数性能。
     */
    private Query optimizeCountQuery(Query query) {
        if (query instanceof CriteriaQuery criteriaQuery) {
            // 创建优化的计数查询
            return new CriteriaQueryBuilder(criteriaQuery.getCriteria())
                    .withTrackTotalHits(true)
                    .build();
        }

        // 如果不是 CriteriaQuery，返回原查询
        return query;
    }

    /**
     * 快速计数查询（用于无条件的简单统计）
     *
     * <p>当查询条件为空时，可以直接使用索引统计，性能最佳。
     */
    public long fastCount(String indexName) {
        try {
            log.debug("开始快速统计索引 {} 的文档总数", indexName);

            // 使用索引统计，性能最佳
            long count = template.count(Query.findAll(), IndexCoordinates.of(indexName));

            log.debug("快速统计完成，索引 {} 共有 {} 个文档", indexName, count);

            return count;

        } catch (Exception e) {
            log.error("快速统计索引 {} 文档数量失败: {}", indexName, e.getMessage(), e);
            throw new RuntimeException("快速统计文档数量失败", e);
        }
    }

    @Override
    public <T> PageResult<T> search(SearchQueryWrapper<T> wrapper) {
        try {
            SearchQuery<T> query = wrapper.build();
            String indexName = query.getIndexOrCollection();

            log.debug("开始执行搜索查询，索引: {}, 条件数量: {}", indexName,
                    query.getConditions() != null ? query.getConditions().size() : 0);

            // 根据查询条件选择最合适的Query类型
            Query esQuery = selectOptimalQueryType(wrapper);

            // 执行搜索
            SearchHits<T> hits = template.search(esQuery, query.getEntityClass(),
                    IndexCoordinates.of(indexName));

            // 构建结果
            List<T> content = hits.getSearchHits().stream()
                    .map(SearchHit::getContent)
                    .collect(Collectors.toList());

            PageResult.PageResultBuilder<T> resultBuilder = PageResult.<T>builder()
                    .content(content)
                    .total(hits.getTotalHits())
                    .page(query.getPage())
                    .size(query.getSize());

            // 处理高亮结果
            if (query.getHighlightFields() != null && !query.getHighlightFields().isEmpty()) {
                Map<String, Map<String, List<String>>> highlights = hits.getSearchHits().stream()
                        .filter(hit -> !hit.getHighlightFields().isEmpty())
                        .collect(Collectors.toMap(
                                SearchHit::getId,
                                SearchHit::getHighlightFields
                        ));
                resultBuilder.highlights(highlights);
            }

            // 处理建议结果
            if (query.getSuggestions() != null && !query.getSuggestions().isEmpty()) {
                Map<String, List<String>> suggestions = buildSuggestions(query);
                resultBuilder.suggestions(suggestions);
            }

            log.debug("搜索查询完成，返回 {} 个文档", content.size());
            return resultBuilder.build();

        } catch (Exception e) {
            log.error("搜索查询失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public <T> T findOne(SearchQueryWrapper<T> wrapper) {
        SearchQuery<T> query = wrapper.build();
        // 构建查询条件
        Criteria criteria = new Criteria();
        Map<String, Object> conditions = ConditionUtils.extractConditions(query.getConditions());

        if (conditions.isEmpty()) {
            throw new IllegalArgumentException("findOne查询必须包含至少一个非空条件");
        }

        for (Map.Entry<String, Object> entry : conditions.entrySet()) {
            criteria = criteria.and(entry.getKey()).is(entry.getValue());
        }

        // 构建查询，限制只返回1条记录
        Query esQuery = new CriteriaQuery(criteria, PageRequest.of(0, 1));
        SearchHits<T> hits = template.search(esQuery, query.getEntityClass(), IndexCoordinates.of(query.getIndexOrCollection()));

        List<T> content = hits.getSearchHits().stream().map(SearchHit::getContent).toList();

        if (content.isEmpty()) {
            return null;
        } else if (content.size() > 1) {
            throw new IllegalStateException("findOne查询返回了多条记录，期望只有一条记录");
        }

        return content.getFirst();
    }

    @Override
    public <T> PageResult<T> dslSearch(StringQueryWrapper<T> wrapper) {
        return internalSearch(wrapper);
    }

    @Override
    public <T> PageResult<T> nativeSearch(NativeQueryWrapper<T> wrapper) {
        return internalSearch(wrapper);
    }

    private <T> PageResult<T> internalSearch(AbstractQueryWrapper<T, ?> wrapper) {
        SearchQuery<T> searchQuery = wrapper.build();
        String indexName = searchQuery.getIndexOrCollection();
        log.debug("开始执行原生查询从索引: {}", indexName);
        // 使用工具类解析原生查询字符串
        Query query = selectOptimalQueryType(wrapper);
        query.setPageable(PageRequest.of(wrapper.getPage(), wrapper.getSize()));

        // 添加排序
        if (wrapper.getSort() != null && !wrapper.getSort().isEmpty()) {
            for (Map.Entry<String, String> entry : wrapper.getSort().entrySet()) {
                String field = entry.getKey();
                String direction = entry.getValue();
                // 设置排序
                if ("asc".equalsIgnoreCase(direction)) {
                    query.addSort(Sort.by(field).ascending());
                } else if ("desc".equalsIgnoreCase(direction)) {
                    query.addSort(Sort.by(field).descending());
                }
            }
        }

        try {
            // 执行查询
            SearchHits<T> hits = template.search(query, wrapper.getEntityClass(),
                    IndexCoordinates.of(indexName));

            List<T> content = hits.getSearchHits().stream()
                    .map(SearchHit::getContent)
                    .collect(Collectors.toList());

            log.debug("原生查询成功，返回 {} 个文档", content.size());

            return PageResult.<T>builder()
                    .content(content)
                    .total(hits.getTotalHits())
                    .page(query.getPageable().getPageNumber())
                    .size(query.getPageable().getPageSize())
                    .build();

        } catch (Exception e) {
            log.error("原生查询失败: {}", e.getMessage(), e);
            throw new RuntimeException("原生查询失败", e);
        }
    }

    /**
     * 构建 Elasticsearch 查询
     *
     * @param wrapper 搜索查询包装器
     * @param <T>     实体类型
     * @return Spring Data Elasticsearch 查询对象
     */
    private <T> Query buildElasticsearchQuery(SearchQueryWrapper<T> wrapper) {
        SearchQuery<T> query = wrapper.build();

        // 使用 Criteria 构建查询
        Criteria criteria = buildCriteriaFromConditions(query.getConditions());

        // 构建基础查询
        Query esQuery = new CriteriaQuery(criteria);

        // 添加分页
        if (query.getPage() >= 0 && query.getSize() > 0) {
            esQuery = new CriteriaQuery(criteria, PageRequest.of(query.getPage(), query.getSize()));
        }

        // 添加排序
        if (query.getSort() != null && !query.getSort().isEmpty()) {
            esQuery = addSortToQuery(esQuery, query.getSort());
        }

        return esQuery;
    }

    /**
     * 从查询条件列表构建 Criteria
     * 正确的方式是使用链式调用构建查询条件
     */
    private Criteria buildCriteriaFromConditions(List<QueryCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return new Criteria();
        }

        // 从第一个条件开始构建
        QueryCondition firstCondition = conditions.getFirst();
        Criteria criteria = buildSingleCriteria(firstCondition);

        // 从第二个条件开始，使用and()方法链式调用
        for (int i = 1; i < conditions.size(); i++) {
            QueryCondition condition = conditions.get(i);
            criteria = criteria.and(buildSingleCriteria(condition));
        }

        return criteria;
    }

    /**
     * 构建单个查询条件的Criteria
     */
    private Criteria buildSingleCriteria(QueryCondition condition) {
        String field = condition.getField();
        Object value = condition.getValue();

        return switch (condition.getOperator()) {
            case EQ -> new Criteria(field).is(value);
            case NE -> new Criteria(field).not().is(value);
            case IN -> new Criteria(field).in((Collection<?>) value);
            case NOT_IN -> new Criteria(field).not().in((Collection<?>) value);
            case GT -> new Criteria(field).greaterThan(value);
            case GE -> new Criteria(field).greaterThanEqual(value);
            case LT -> new Criteria(field).lessThan(value);
            case LE -> new Criteria(field).lessThanEqual(value);
            case BETWEEN -> {
                Map<String, Object> range = (Map<String, Object>) value;
                yield new Criteria(field).between(range.get("from"), range.get("to"));
            }
            case LIKE -> new Criteria(field).contains(value.toString());
            case EXISTS -> new Criteria(field).exists();
            case NOT_EXISTS -> new Criteria(field).not().exists();
            case OR -> new Criteria(field).is(value.toString());
            default -> {
                log.warn("未支持的查询操作符: {}", condition.getOperator());
                yield new Criteria(field).is(value);
            }
        };
    }

    /**
     * 为查询添加排序
     */
    private Query addSortToQuery(Query query, Map<String, String> sortMap) {
        if (sortMap == null || sortMap.isEmpty()) {
            return query;
        }

        // 创建排序列表
        List<Sort.Order> sortOrders = new ArrayList<>();

        for (Map.Entry<String, String> entry : sortMap.entrySet()) {
            String field = entry.getKey();
            String direction = entry.getValue();

            if ("asc".equalsIgnoreCase(direction)) {
                sortOrders.add(Sort.Order.asc(field));
            } else if ("desc".equalsIgnoreCase(direction)) {
                sortOrders.add(Sort.Order.desc(field));
            }
        }

        // 创建新的查询对象，包含排序
        if (query instanceof CriteriaQuery criteriaQuery) {
            return new CriteriaQueryBuilder(criteriaQuery.getCriteria())
                    .withPageable(criteriaQuery.getPageable())
                    .withSort(Sort.by(sortOrders))
                    .build();
        } else {
            // 如果不是 CriteriaQuery，创建一个新的查询
            return new NativeQueryBuilder()
                    .withQuery(query)
                    .withPageable(query.getPageable())
                    .withSort(Sort.by(sortOrders))
                    .build();
        }
    }

    /**
     * 构建建议结果
     */
    private <T> Map<String, List<String>> buildSuggestions(SearchQuery<T> query) {
        Map<String, List<String>> result = new HashMap<>();

        for (Map.Entry<String, Object> entry : query.getSuggestions().entrySet()) {
            String suggestName = entry.getKey();
            Map<String, Object> suggestConfig = (Map<String, Object>) entry.getValue();
            String field = (String) suggestConfig.get("field");
            Integer size = suggestConfig.get("size") != null ? (Integer) suggestConfig.get("size") : 5;

            try {
                // 构建completion suggester请求
                var suggestRequest = SearchRequest.of(s -> s
                        .index(query.getIndexOrCollection())
                        .suggest(suggest -> suggest
                                .suggesters(suggestName, suggester -> suggester
                                        .completion(completion -> completion
                                                .field(field)
                                                .size(size)
                                                .skipDuplicates(true)
                                        )
                                )
                        )
                );

                // 执行suggest查询
                var response = elasticsearchClient.search(suggestRequest, query.getEntityClass());

                // 解析completion suggester结果
                List<String> suggestions = new ArrayList<>();
                if (response.suggest() != null && response.suggest().get(suggestName) != null) {
                    var suggestOptions = response.suggest().get(suggestName).getFirst().completion().options();
                    for (var option : suggestOptions) {
                        if (option.text() != null) {
                            suggestions.add(option.text());
                        }
                    }
                }

                // 如果completion suggester没有结果，尝试term suggester
                if (suggestions.isEmpty()) {
                    var termSuggestRequest = SearchRequest.of(s -> s
                            .index(query.getIndexOrCollection())
                            .suggest(suggest -> suggest
                                    .suggesters(suggestName, suggester -> suggester
                                            .term(term -> term
                                                    .field(field)
                                                    .size(size)
                                            )
                                    )
                            )
                    );

                    var termResponse = elasticsearchClient.search(termSuggestRequest, query.getEntityClass());

                    if (termResponse.suggest() != null && termResponse.suggest().get(suggestName) != null) {
                        var termOptions = termResponse.suggest().get(suggestName).getFirst().term().options();
                        for (var option : termOptions) {
                            if (option.text() != null) {
                                suggestions.add(option.text());
                            }
                        }
                    }
                }

                result.put(suggestName, suggestions);

            } catch (Exception e) {
                // 记录错误日志并返回空列表
                log.error("Suggest查询失败: {}", e.getMessage(), e);
                result.put(suggestName, new ArrayList<>());
            }
        }

        return result;
    }

    /**
     * 选择最优的Query类型并构建查询
     *
     * @param wrapper 搜索查询包装器
     * @param <T>     实体类型
     * @return 构建好的Query对象
     */
    private <T> Query selectOptimalQueryType(AbstractQueryWrapper<T, ?> wrapper) {
        SearchQuery<T> query = wrapper.build();
        return switch (query.getType()) {
            case BASIC -> buildCriteriaQuery(query);
            case STRING -> buildStringQuery(query);
            case NATIVE -> buildNativeQuery(query);
        };
    }


    /**
     * 构建CriteriaQuery
     *
     * @param query 搜索查询包装器
     * @param <T>   实体类型
     * @return CriteriaQuery对象
     */
    private <T> Query buildCriteriaQuery(SearchQuery<T> query) {

        // 构建Criteria
        Criteria criteria = buildCriteriaFromConditions(query.getConditions());

        // 创建基础查询
        CriteriaQueryBuilder queryBuilder = new CriteriaQueryBuilder(criteria);

        // 添加分页
        if (query.getPage() >= 0 && query.getSize() > 0) {
            queryBuilder.withPageable(PageRequest.of(query.getPage(), query.getSize()));
        }

        // 添加排序
        if (query.getSort() != null && !query.getSort().isEmpty()) {
            List<Sort.Order> sortOrders = buildSortOrders(query.getSort());
            queryBuilder.withSort(Sort.by(sortOrders));
        }

        // 添加高亮
        if (query.getHighlightFields() != null && !query.getHighlightFields().isEmpty()) {
            List<HighlightField> highlightFields = query.getHighlightFields().stream()
                    .map(HighlightField::new)
                    .collect(Collectors.toList());
            Highlight highlight = new Highlight(highlightFields);
            queryBuilder.withHighlightQuery(new HighlightQuery(highlight, query.getEntityClass()));
        }
        return queryBuilder.withTrackTotalHits(query.getTrackTotalHits()).build();
    }

    private <T> Query buildNativeQuery(SearchQuery<T> query) {
        return NativeQuery.builder().withTrackTotalHits(query.getTrackTotalHits()).withQuery(query.getNativeQuery()).build();
    }

    /**
     * 构建NativeQuery
     *
     * @param query 搜索查询包装器
     * @param <T>   实体类型
     * @return NativeQuery对象
     */
    private <T> Query buildStringQuery(SearchQuery<T> query) {
        return new StringQueryBuilder(query.getStringQuery()).withTrackTotalHits(query.getTrackTotalHits()).build();
    }

    /**
     * 构建排序订单列表
     *
     * @param sortMap 排序映射
     * @return 排序订单列表
     */
    private List<Sort.Order> buildSortOrders(Map<String, String> sortMap) {
        List<Sort.Order> sortOrders = new ArrayList<>();

        for (Map.Entry<String, String> entry : sortMap.entrySet()) {
            String field = entry.getKey();
            String direction = entry.getValue();

            if ("asc".equalsIgnoreCase(direction)) {
                sortOrders.add(Sort.Order.asc(field));
            } else if ("desc".equalsIgnoreCase(direction)) {
                sortOrders.add(Sort.Order.desc(field));
            }
        }

        return sortOrders;
    }
}

