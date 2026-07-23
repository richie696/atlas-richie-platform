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
package com.richie.component.vector.service.impl;

import com.richie.component.ai.service.RerankService;
import com.richie.component.vector.config.VectorProperties;
import com.richie.component.vector.model.IndexInfo;
import com.richie.component.vector.model.IndexStatus;
import com.richie.component.vector.model.Modality;
import com.richie.component.vector.model.VectorContent;
import com.richie.component.vector.model.VectorRecord;
import com.richie.component.vector.service.VectorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.search.FTCreateParams;
import redis.clients.jedis.search.IndexDataType;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.RediSearchUtil;
import redis.clients.jedis.search.SearchResult;
import redis.clients.jedis.search.schemafields.NumericField;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TagField;
import redis.clients.jedis.search.schemafields.TextField;
import redis.clients.jedis.search.schemafields.VectorField;
import redis.clients.jedis.search.schemafields.VectorField.VectorAlgorithm;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "platform.component.vector", name = "provider", havingValue = "redis")
public class RedisVectorServiceImpl extends AbstractVectorService implements VectorService, InitializingBean {

    public RedisVectorServiceImpl(@Autowired(required = false) RerankService rerankService,
                                  VectorStore vectorStore,
                                  @Qualifier("aiEmbeddingModel") EmbeddingModel embeddingModel) {
        super(rerankService, vectorStore, embeddingModel);
    }

    @Override
    public void afterPropertiesSet() {
        checkRedisStackAvailability();
    }

    private void checkRedisStackAvailability() {
        if (!(vectorStore instanceof RedisVectorStore rvs)) {
            return;
        }
        Optional<RedisClient> nativeClient = rvs.getNativeClient();
        if (nativeClient.isEmpty()) {
            throw new IllegalStateException(
                    "Redis向量搜索不可用：无法获取Jedis客户端。"
                    + "请确保Redis版本>=7.0且已加载RediSearch模块（Redis Stack）");
        }
        RedisClient jedis = nativeClient.get();
        try {
            jedis.ftList();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Redis向量搜索不可用：RediSearch模块未检测到。"
                    + "请确认Redis已安装Redis Stack或RediSearch模块（版本>=2.4），"
                    + "并启用了向量搜索功能。原始错误: " + e.getMessage(), e);
        }
        log.info("Redis Stack向量搜索模块检测正常");
    }

    // ==================== AbstractVectorService 抽象方法实现 ====================

    @Override
    protected List<Document> similaritySearchByVector(String indexName, float[] vector, int limit, double minScore) {
        if (!(vectorStore instanceof RedisVectorStore rvs)) {
            throw new UnsupportedOperationException("当前VectorStore不支持向量搜索");
        }
        Optional<RedisClient> clientOpt = rvs.getNativeClient();
        if (clientOpt.isEmpty()) {
            throw new IllegalStateException("无法获取Jedis客户端");
        }
        RedisClient jedis = clientOpt.get();
        try {
            String queryStr = "*=>[KNN " + limit + " @embedding $BLOB AS score]";
            Query query = new Query(queryStr)
                    .addParam("BLOB", RediSearchUtil.toByteArray(vector))
                    .returnFields("id", "content", "score")
                    .limit(0, limit)
                    .dialect(2);
            SearchResult result = jedis.ftSearch(indexName, query);
            return result.getDocuments().stream()
                    .map(doc -> {
                        double distance = 0.0;
                        if (doc.hasProperty("score")) {
                            try {
                                distance = Double.parseDouble(doc.getString("score"));
                            } catch (NumberFormatException ignored) {
                            }
                        }
                        // Redis KNN 返回的是距离（越小越相似），转为相似度语义
                        double similarity = Math.max(0.0, 1.0 - distance);
                        if (similarity < minScore) {
                            return null;
                        }
                        String content = doc.hasProperty("content") ? doc.getString("content") : "";
                        String docId = stripIndexPrefix(doc.getId(), indexName);
                        return Document.builder()
                                .id(docId)
                                .text(content)
                                .metadata(new HashMap<>())
                                .score(similarity)
                                .build();
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("向量搜索失败: " + indexName, e);
        }
    }

    @Override
    protected void addEmbeddings(String indexName, List<Document> docs) {
        if (!(vectorStore instanceof RedisVectorStore rvs)) {
            throw new UnsupportedOperationException("当前VectorStore不支持 addEmbeddings");
        }
        rvs.add(docs);
    }

    @Override
    protected void deleteByIds(String indexName, List<String> ids) {
        if (!(vectorStore instanceof RedisVectorStore rvs)) {
            throw new UnsupportedOperationException("当前VectorStore不支持 deleteByIds");
        }
        rvs.delete(ids);
    }

    @Override
    protected List<VectorRecord> getByIds(String indexName, List<String> ids) {
        if (!(vectorStore instanceof RedisVectorStore rvs)) {
            throw new UnsupportedOperationException("当前VectorStore不支持 getByIds");
        }
        Optional<RedisClient> clientOpt = rvs.getNativeClient();
        if (clientOpt.isEmpty()) {
            throw new IllegalStateException("无法获取Jedis客户端");
        }
        RedisClient jedis = clientOpt.get();
        List<VectorRecord> result = new ArrayList<>();
        for (String id : ids) {
            try {
                var path2 = redis.clients.jedis.json.Path2.of("$");
                List<?> jsonResults = jedis.jsonMGet(path2, new String[]{indexName + ":" + id});
                if (jsonResults == null || jsonResults.isEmpty()) {
                    continue;
                }
                VectorRecord record = parseJsonToRecord(id, indexName, jsonResults.get(0));
                if (record != null) {
                    result.add(record);
                }
            } catch (Exception e) {
                log.warn("getByIds 单条失败: id={}, error={}", id, e.getMessage());
            }
        }
        return result;
    }

    @Override
    protected List<VectorRecord> listDocumentsImpl(String indexName, int offset, int limit) {
        if (!(vectorStore instanceof RedisVectorStore rvs)) {
            throw new UnsupportedOperationException("当前VectorStore不支持列表查询");
        }
        Optional<RedisClient> clientOpt = rvs.getNativeClient();
        if (clientOpt.isEmpty()) {
            throw new IllegalStateException("无法获取Jedis客户端");
        }
        RedisClient jedis = clientOpt.get();
        try {
            String prefix = indexName + ":";
            ScanParams scanParams = new ScanParams().match(prefix + "*").count(500);
            String cursor = ScanParams.SCAN_POINTER_START;
            List<String> allKeys = new ArrayList<>();
            while (true) {
                var scanResult = jedis.scan(cursor, scanParams);
                allKeys.addAll(scanResult.getResult());
                if (scanResult.isCompleteIteration()) break;
                cursor = scanResult.getCursor();
            }
            int from = Math.min(offset, allKeys.size());
            int to = Math.min(from + limit, allKeys.size());
            if (from >= to) return List.of();
            List<String> pageKeys = allKeys.subList(from, to);
            var path2 = redis.clients.jedis.json.Path2.of("$");
            List<VectorRecord> docs = new ArrayList<>();
            for (int i = 0; i < pageKeys.size(); i += LIST_DOCUMENTS_BATCH_SIZE) {
                int end = Math.min(i + LIST_DOCUMENTS_BATCH_SIZE, pageKeys.size());
                List<String> batch = pageKeys.subList(i, end);
                List<?> jsonResults = jedis.jsonMGet(path2, batch.toArray(new String[0]));
                for (int j = 0; j < batch.size(); j++) {
                    Object item = jsonResults.get(j);
                    if (item == null) continue;
                    String docKey = batch.get(j);
                    String docId = docKey.contains(":")
                            ? docKey.substring(docKey.indexOf(":") + 1) : docKey;
                    VectorRecord record = parseJsonToRecord(docId, indexName, item);
                    if (record != null) {
                        docs.add(record);
                    }
                }
            }
            return docs;
        } catch (Exception e) {
            throw new RuntimeException("listDocuments失败: " + indexName, e);
        }
    }

    // ==================== AbstractVectorService *Impl 索引管理方法 ====================

    @Override
    protected void createIndexImpl(String indexName, VectorProperties.IndexConfig config) {
        if (!(vectorStore instanceof RedisVectorStore rvs)) {
            throw new UnsupportedOperationException("当前VectorStore不支持创建索引");
        }
        Optional<RedisClient> clientOpt = rvs.getNativeClient();
        if (clientOpt.isEmpty()) {
            throw new IllegalStateException("无法获取Jedis客户端");
        }
        RedisClient jedis = clientOpt.get();

        Map<String, Object> vectorAttrs = new HashMap<>();
        vectorAttrs.put("DIM", config.getDimension() != null ? config.getDimension() : 1536);
        vectorAttrs.put("TYPE", "FLOAT32");
        String metric = config.getMetric() != null ? config.getMetric() : "cosine";
        switch (metric.toLowerCase()) {
            case "euclidean", "l2" -> vectorAttrs.put("DISTANCE_METRIC", "L2");
            case "dot", "ip" -> vectorAttrs.put("DISTANCE_METRIC", "IP");
            default -> vectorAttrs.put("DISTANCE_METRIC", "COSINE");
        }
        String indexType = config.getIndexType() != null ? config.getIndexType() : "hnsw";
        VectorAlgorithm algorithm = "hnsw".equalsIgnoreCase(indexType)
                ? VectorAlgorithm.HNSW : VectorAlgorithm.FLAT;
        if ("hnsw".equalsIgnoreCase(indexType) && config.getIndexParams() != null) {
            Object m = config.getIndexParams().get("M");
            if (m != null) vectorAttrs.put("M", ((Number) m).intValue());
            Object efConstr = config.getIndexParams().get("efConstruction");
            if (efConstr != null) vectorAttrs.put("EF_CONSTRUCTION", ((Number) efConstr).intValue());
            Object efRuntime = config.getIndexParams().get("efRuntime");
            if (efRuntime != null) vectorAttrs.put("EF_RUNTIME", ((Number) efRuntime).intValue());
        }
        List<SchemaField> fields = new ArrayList<>();
        fields.add(TextField.of("$.content").as("content"));
        fields.add(VectorField.builder()
                .fieldName("$.embedding")
                .algorithm(algorithm)
                .attributes(vectorAttrs)
                .as("embedding")
                .build());
        if (config.getAdditionalFields() != null) {
            for (Map.Entry<String, Object> entry : config.getAdditionalFields().entrySet()) {
                Object v = entry.getValue();
                if (v instanceof Number) {
                    fields.add(NumericField.of("$." + entry.getKey()).as(entry.getKey()));
                } else if (v instanceof String) {
                    fields.add(TagField.of("$." + entry.getKey()).as(entry.getKey()));
                } else {
                    fields.add(TextField.of("$." + entry.getKey()).as(entry.getKey()));
                }
            }
        }
        String prefix = indexName + ":";
        try {
            String resp = jedis.ftCreate(indexName,
                    FTCreateParams.createParams().on(IndexDataType.JSON).addPrefix(prefix),
                    fields);
            if (!"OK".equals(resp)) {
                throw new RuntimeException("创建索引失败: " + resp);
            }
            log.info("Redis向量索引创建成功: {}", indexName);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                log.warn("索引已存在: {}", indexName);
            } else {
                throw new RuntimeException("创建索引失败: " + indexName, e);
            }
        }
    }

    @Override
    protected void deleteIndexImpl(String indexName) {
        if (!(vectorStore instanceof RedisVectorStore rvs)) {
            throw new UnsupportedOperationException("当前VectorStore不支持删除索引");
        }
        Optional<RedisClient> clientOpt = rvs.getNativeClient();
        if (clientOpt.isEmpty()) {
            throw new IllegalStateException("无法获取Jedis客户端");
        }
        RedisClient jedis = clientOpt.get();
        try {
            jedis.ftDropIndex(indexName);
            log.info("Redis向量索引删除成功: {}", indexName);
        } catch (Exception e) {
            throw new RuntimeException("删除索引失败: " + indexName, e);
        }
    }

    @Override
    protected boolean indexExistsImpl(String indexName) {
        if (!(vectorStore instanceof RedisVectorStore rvs)) {
            return false;
        }
        Optional<RedisClient> clientOpt = rvs.getNativeClient();
        if (clientOpt.isEmpty()) {
            return false;
        }
        RedisClient jedis = clientOpt.get();
        try {
            return jedis.ftList().contains(indexName);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 列出 Redis 中的全部 RediSearch 索引。
     *
     * @return 索引信息列表
     * @throws UnsupportedOperationException 当前 VectorStore 不是 Redis 实现时抛出
     * @throws IllegalStateException 无法获取 Jedis 客户端时抛出
     * @throws RuntimeException RediSearch 索引查询失败时抛出
     */
    @Override
    protected List<IndexInfo> listIndexesImpl() {
        if (!(vectorStore instanceof RedisVectorStore rvs)) {
            throw new UnsupportedOperationException("当前VectorStore不支持列出索引");
        }
        Optional<RedisClient> clientOpt = rvs.getNativeClient();
        if (clientOpt.isEmpty()) {
            throw new IllegalStateException("无法获取Jedis客户端");
        }
        try {
            List<IndexInfo> indexes = clientOpt.get().ftList().stream()
                    .map(this::describeIndexImpl)
                    .toList();
            log.debug("Redis 向量索引列表查询完成，数量={}", indexes.size());
            return indexes;
        } catch (Exception e) {
            throw new RuntimeException("列出 Redis 向量索引失败", e);
        }
    }

    /**
     * 获取指定 RediSearch 索引的描述信息。
     *
     * @param indexName 索引名称
     * @return 包含维度、距离度量和文档数量的索引信息
     * @throws UnsupportedOperationException 当前 VectorStore 不是 Redis 实现时抛出
     * @throws IllegalStateException 无法获取 Jedis 客户端时抛出
     * @throws RuntimeException RediSearch 索引信息查询失败时抛出
     */
    @Override
    protected IndexInfo describeIndexImpl(String indexName) {
        IndexInfo indexInfo = getIndexStatsImpl(indexName);
        log.debug("Redis 向量索引描述查询完成: indexName={}, dimension={}, metric={}, documentCount={}",
                indexName, indexInfo.dimension(), indexInfo.metric(), indexInfo.documentCount());
        return indexInfo;
    }

    /**
     * 尝试更新 RediSearch 索引配置。
     * <p>
     * RediSearch 不支持原地修改向量索引配置，需要调用方自行删除并重建索引。
     *
     * @param indexName 索引名称
     * @param config 新的索引配置
     * @return 固定返回 false，表示不支持原地更新
     */
    @Override
    protected boolean updateIndexConfigImpl(String indexName, VectorProperties.IndexConfig config) {
        log.warn("Redis RediSearch 不支持原地修改向量索引配置，请删除后重建: indexName={}", indexName);
        return false;
    }

    /**
     * 尝试优化 RediSearch 向量索引。
     *
     * @param indexName 索引名称
     * @return 不返回结果
     * @throws UnsupportedOperationException RediSearch 不提供显式索引优化 API
     */
    @Override
    protected boolean optimizeImpl(String indexName) {
        return throwUnsupportedOps("optimize", indexName, "redis");
    }

    /**
     * 尝试为 RediSearch 向量索引创建别名。
     *
     * @param indexName 索引名称
     * @param alias 别名
     * @return 不返回结果
     * @throws UnsupportedOperationException RediSearch 不支持向量索引别名
     */
    @Override
    protected boolean createAliasImpl(String indexName, String alias) {
        return throwUnsupportedOps("createAlias", indexName, "redis");
    }

    /**
     * 尝试切换 RediSearch 向量索引别名。
     *
     * @param oldIndexName 旧索引名称
     * @param newIndexName 新索引名称
     * @param alias 别名
     * @return 不返回结果
     * @throws UnsupportedOperationException RediSearch 不支持向量索引别名切换
     */
    @Override
    protected boolean switchAliasImpl(String oldIndexName, String newIndexName, String alias) {
        return throwUnsupportedOps("switchAlias", newIndexName, "redis");
    }

    /**
     * 尝试通过 VectorService 备份 Redis 数据。
     *
     * @param indexName 索引名称
     * @param targetPath 备份目标路径
     * @return 不返回结果
     * @throws UnsupportedOperationException Redis 备份需要通过进程外命令执行
     */
    @Override
    protected boolean backupImpl(String indexName, String targetPath) {
        return throwUnsupportedOps("backup", indexName, "redis");
    }

    /**
     * 尝试通过 VectorService 恢复 Redis 数据。
     *
     * @param sourcePath 备份源路径
     * @param indexName 索引名称
     * @return 不返回结果
     * @throws UnsupportedOperationException Redis 恢复需要通过进程外操作执行
     */
    @Override
    protected boolean restoreImpl(String sourcePath, String indexName) {
        return throwUnsupportedOps("restore", indexName, "redis");
    }

    /**
     * 获取指定 RediSearch 索引的运行统计信息。
     *
     * @param indexName 索引名称
     * @return 包含文档数、索引状态及 {@code FT.INFO} 原始统计项的索引信息
     * @throws UnsupportedOperationException 当前 VectorStore 不是 Redis 实现时抛出
     * @throws IllegalStateException 无法获取 Jedis 客户端时抛出
     * @throws RuntimeException RediSearch 索引信息查询失败时抛出
     */
    @Override
    protected IndexInfo getIndexStatsImpl(String indexName) {
        if (!(vectorStore instanceof RedisVectorStore rvs)) {
            throw new UnsupportedOperationException("当前VectorStore不支持获取索引统计");
        }
        Optional<RedisClient> clientOpt = rvs.getNativeClient();
        if (clientOpt.isEmpty()) {
            throw new IllegalStateException("无法获取Jedis客户端");
        }
        try {
            Map<String, Object> info = clientOpt.get().ftInfo(indexName);
            Map<String, Object> vectorAttributes = findVectorAttributes(info.get("attributes"));
            Object dimensionValue = getIgnoreCase(vectorAttributes, "dim");
            if (dimensionValue == null) {
                dimensionValue = getIgnoreCase(info, "dimension");
            }
            Object metricValue = getIgnoreCase(vectorAttributes, "distance_metric");
            if (metricValue == null) {
                metricValue = getIgnoreCase(info, "distance_metric");
            }
            Object algorithmValue = getIgnoreCase(vectorAttributes, "algorithm");
            if (algorithmValue == null) {
                algorithmValue = getIgnoreCase(info, "algorithm");
            }
            Integer dimension = toInteger(dimensionValue);
            Long documentCount = toLong(info.get("num_docs"));
            Long indexing = toLong(info.get("indexing"));
            IndexStatus status = indexing != null && indexing > 0
                    ? IndexStatus.CREATING : IndexStatus.READY;
            Map<String, Object> metadata = new LinkedHashMap<>(info);
            IndexInfo indexInfo = new IndexInfo(
                    indexName,
                    Modality.TEXT,
                    dimension,
                    metricValue != null ? metricValue.toString() : null,
                    algorithmValue != null ? algorithmValue.toString() : null,
                    status,
                    documentCount != null ? documentCount : 0L,
                    null,
                    Instant.now(),
                    metadata);
            log.debug("Redis 向量索引统计查询完成: indexName={}, numDocs={}, hashIndexingFailures={}, indexing={}",
                    indexName, indexInfo.documentCount(), info.get("hash_indexing_failures"), info.get("indexing"));
            return indexInfo;
        } catch (Exception e) {
            throw new RuntimeException("获取 Redis 向量索引统计失败: %s".formatted(indexName), e);
        }
    }

    @Override
    protected VectorProperties.IndexConfig getIndexConfigImpl(String indexName) {
        if (!(vectorStore instanceof RedisVectorStore rvs)) {
            throw new UnsupportedOperationException("当前VectorStore不支持获取索引配置");
        }
        Optional<RedisClient> clientOpt = rvs.getNativeClient();
        if (clientOpt.isEmpty()) {
            throw new IllegalStateException("无法获取Jedis客户端");
        }
        RedisClient jedis = clientOpt.get();
        try {
            Map<String, Object> info = jedis.ftInfo(indexName);
            VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
            config.setName(indexName);
            if (info.containsKey("num_docs")) {
                config.setAdditionalFields(Map.of("numDocs", info.get("num_docs")));
            }
            return config;
        } catch (Exception e) {
            throw new RuntimeException("获取索引配置失败: " + indexName, e);
        }
    }

    @Override
    protected long truncateIndexImpl(String indexName) {
        if (!(vectorStore instanceof RedisVectorStore rvs)) {
            throw new UnsupportedOperationException("当前VectorStore不支持truncateIndex");
        }
        Optional<RedisClient> clientOpt = rvs.getNativeClient();
        if (clientOpt.isEmpty()) {
            throw new IllegalStateException("无法获取Jedis客户端");
        }
        RedisClient jedis = clientOpt.get();
        long deleted = 0;
        String prefix = indexName + ":";
        ScanParams scanParams = new ScanParams().match(prefix + "*").count(500);
        String cursor = ScanParams.SCAN_POINTER_START;
        do {
            var scanResult = jedis.scan(cursor, scanParams);
            List<String> keys = scanResult.getResult();
            if (!keys.isEmpty()) {
                for (int i = 0; i < keys.size(); i += 100) {
                    int end = Math.min(i + 100, keys.size());
                    deleted += jedis.unlink(keys.subList(i, end).toArray(new String[0]));
                }
            }
            if (scanResult.isCompleteIteration()) break;
            cursor = scanResult.getCursor();
        } while (true);
        log.info("Redis 清空索引 [{}] 完成，实际删除键数={}", indexName, deleted);
        return deleted;
    }

    @Override
    protected long countDocumentsImpl(String indexName) {
        if (!(vectorStore instanceof RedisVectorStore rvs)) {
            throw new UnsupportedOperationException("当前VectorStore不支持计数");
        }
        Optional<RedisClient> clientOpt = rvs.getNativeClient();
        if (clientOpt.isEmpty()) {
            throw new IllegalStateException("无法获取Jedis客户端");
        }
        RedisClient jedis = clientOpt.get();
        try {
            Map<String, Object> info = jedis.ftInfo(indexName);
            Object numDocs = info.get("num_docs");
            if (numDocs instanceof Long) return (Long) numDocs;
            if (numDocs instanceof Integer) return ((Integer) numDocs).longValue();
            return 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private Map<String, Object> findVectorAttributes(Object attributes) {
        if (!(attributes instanceof List<?> attributeList)) {
            return Map.of();
        }
        for (Object attribute : attributeList) {
            Map<String, Object> attributeMap = toAttributeMap(attribute);
            Object type = getIgnoreCase(attributeMap, "type");
            if (type != null && "VECTOR".equalsIgnoreCase(type.toString())) {
                return attributeMap;
            }
        }
        return Map.of();
    }

    private Map<String, Object> toAttributeMap(Object attribute) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (attribute instanceof Map<?, ?> attributeMap) {
            attributeMap.forEach((key, value) -> {
                if (key != null) {
                    result.put(key.toString(), value);
                }
            });
            return result;
        }
        if (attribute instanceof List<?> attributeValues) {
            for (int i = 0; i + 1 < attributeValues.size(); i += 2) {
                Object key = attributeValues.get(i);
                if (key != null) {
                    result.put(key.toString(), attributeValues.get(i + 1));
                }
            }
        }
        return result;
    }

    private Object getIgnoreCase(Map<String, Object> values, String key) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.valueOf(value.toString());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.valueOf(value.toString());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    // ==================== 内部工具 ====================

    /**
     * 从 Redis JSON 对象构造 {@link VectorRecord}。若无法抽取非空文本内容则返回 null（跳过该条目）。
     * <p>
     * Jedis {@code jsonMGet(Path2, String...)} 返回 {@code List<JSONArray>} — 每个键一个 JSONArray,
     * 其首个元素为该键对应的根 JSON 对象({@code JSONObject})。
     */
    private VectorRecord parseJsonToRecord(String id, String indexName, Object jsonElement) {
        try {
            String textContent = null;
            if (jsonElement instanceof org.json.JSONArray jsonArray && jsonArray.length() > 0) {
                Object first = jsonArray.get(0);
                if (first instanceof org.json.JSONObject jsonObject) {
                    Object content = jsonObject.opt("content");
                    if (content != null) textContent = content.toString();
                } else if (first instanceof java.util.Map<?, ?> map) {
                    Object content = map.get("content");
                    if (content != null) textContent = content.toString();
                }
            } else if (jsonElement instanceof java.util.List<?> list && !list.isEmpty()) {
                Object first = list.getFirst();
                if (first instanceof java.util.Map<?, ?> map) {
                    Object content = map.get("content");
                    if (content != null) textContent = content.toString();
                }
            }
            // TextContent 紧凑构造器不接受 blank text；为空时跳过该条目
            if (textContent == null || textContent.isBlank()) {
                return null;
            }
            return new VectorRecord()
                    .setId(id)
                    .setIndexName(indexName)
                    .setContent(new VectorContent.TextContent(textContent, "text/plain"));
        } catch (Exception e) {
            return null;
        }
    }

    private String stripIndexPrefix(String redisKey, String indexName) {
        String prefix = indexName + ":";
        return redisKey.startsWith(prefix) ? redisKey.substring(prefix.length()) : redisKey;
    }
}
