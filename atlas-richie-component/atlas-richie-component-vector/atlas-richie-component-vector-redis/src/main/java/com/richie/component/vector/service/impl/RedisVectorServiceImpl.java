package com.richie.component.vector.service.impl;

import com.richie.component.vector.config.VectorProperties;
import com.richie.component.vector.model.VectorDocument;
import com.richie.component.vector.model.VectorSearchResult;
import com.richie.component.vector.service.VectorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.InitializingBean;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "platform.component.vector", name = "provider", havingValue = "redis")
public class RedisVectorServiceImpl extends VectorServiceImpl implements VectorService, InitializingBean {

    public RedisVectorServiceImpl(VectorStore vectorStore,
                                  @Qualifier("aiEmbeddingModel") EmbeddingModel embeddingModel) {
        super(vectorStore, embeddingModel);
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

    @Override
    public List<VectorSearchResult> searchByVector(String indexName, float[] queryVector, int limit) {
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
                    .addParam("BLOB", RediSearchUtil.toByteArray(queryVector))
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
                        return VectorSearchResult.of(
                                doc.getId(),
                                doc.hasProperty("content") ? doc.getString("content") : "",
                                similarity,
                                null
                        );
                    })
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException("向量搜索失败: " + indexName, e);
        }
    }

    @Override
    public void createIndex(String indexName, VectorProperties.IndexConfig config) {
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
    public void deleteIndex(String indexName) {
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
    public boolean indexExists(String indexName) {
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

    @Override
    public VectorProperties.IndexConfig getIndexConfig(String indexName) {
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
    public long countDocuments(String indexName) {
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

    @Override
    protected List<VectorDocument> listDocumentsHandler(String indexName, int offset, int limit) {
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
            List<VectorDocument> docs = new ArrayList<>();
            for (int i = 0; i < pageKeys.size(); i += LIST_DOCUMENTS_BATCH_SIZE) {
                int end = Math.min(i + LIST_DOCUMENTS_BATCH_SIZE, pageKeys.size());
                List<String> batch = pageKeys.subList(i, end);
                List<?> jsonResults = jedis.jsonMGet(path2, batch.toArray(new String[0]));
                for (int j = 0; j < batch.size(); j++) {
                    Object item = jsonResults.get(j);
                    if (item == null) continue;
                    VectorDocument vd = parseJsonToDocument(batch.get(j), item);
                    if (vd != null) docs.add(vd);
                }
            }
            return docs;
        } catch (Exception e) {
            throw new RuntimeException("listDocuments失败: " + indexName, e);
        }
    }

    private VectorDocument parseJsonToDocument(String key, Object jsonElement) {
        try {
            var vd = new VectorDocument();
            vd.setId(key.contains(":") ? key.substring(key.indexOf(":") + 1) : key);
            if (jsonElement instanceof java.util.List<?> list && !list.isEmpty()) {
                Object first = list.getFirst();
                if (first instanceof java.util.Map<?, ?> map) {
                    Object content = map.get("content");
                    if (content != null) vd.setContent(content.toString());
                }
            }
            return vd;
        } catch (Exception e) {
            return null;
        }
    }
}
