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

import com.richie.context.utils.data.JsonUtils;
import com.richie.component.vector.config.MilvusConfig;
import com.richie.component.vector.config.VectorProperties;
import com.richie.component.vector.model.IndexInfo;
import com.richie.component.vector.model.IndexStatus;
import com.richie.component.vector.model.Modality;
import com.richie.component.vector.model.VectorRecord;
import org.springframework.ai.document.Document;
import com.richie.component.ai.service.RerankService;
import com.richie.component.vector.service.VectorService;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.*;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.*;
import io.milvus.param.control.ManualCompactParam;
import io.milvus.param.alias.AlterAliasParam;
import io.milvus.param.alias.CreateAliasParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Milvus向量数据库服务实现类
 *
 * <p>提供基于Milvus的高性能向量存储和检索能力，实现VectorService接口。
 * 通过Spring条件配置实现与Spring AI VectorStore的集成，支持向量相似度搜索功能。</p>
 *
  * <p>该实现支持：</p>
  * <ul>
  *   <li>动态Collection创建，可配置主键、向量维度、索引类型等</li>
  *   <li>多种索引类型（HNSW、IVF、FLAT等）</li>
  *   <li>多种距离度量方式（余弦相似度、欧氏距离、点积）</li>
  *   <li>向量相似度搜索，返回匹配的文档及相似度分数</li>
  * </ul>
 *
 * @author richie696
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "platform.component.vector", name = "provider", havingValue = "milvus")
public class MilvusVectorServiceImpl extends AbstractVectorService implements VectorService {

    private final MilvusConfig milvusConfig;
    private final MilvusServiceClient milvusClient;

    private static final int DEFAULT_DIMENSION = 1536;
    private static final List<String> SEARCH_OUTPUT_FIELDS = List.of("id", "content");

    /**
     * 构造方法，注入Milvus客户端和配置
     *
     * <p>使用@Qualifier注解指定EmbeddingModel bean，确保注入正确的嵌入模型实例。</p>
     *
     * @param rerankService 重排序服务（可选）
     * @param vectorStore    Spring AI VectorStore，用于文档存储和检索
     * @param embeddingModel 嵌入模型，用于将文本转换为向量
     * @param milvusConfig   Milvus配置，包含连接信息和默认参数
     * @param milvusClient   Milvus客户端，提供与Milvus服务的通信能力
     */
    @Autowired
    public MilvusVectorServiceImpl(@Autowired(required = false) RerankService rerankService,
                                   VectorStore vectorStore,
                                   @Qualifier("aiEmbeddingModel") EmbeddingModel embeddingModel,
                                   MilvusConfig milvusConfig, MilvusServiceClient milvusClient) {
        super(rerankService, vectorStore, embeddingModel);
        this.milvusConfig = milvusConfig;
        this.milvusClient = milvusClient;
    }

    /**
     * 创建Milvus集合（Collection），支持动态字段配置
     *
     * <p>Phase B 重构：复用 {@link #buildFieldTypes(VectorProperties.IndexConfig)}
     * 构建完整字段定义（id + vector + content + metadata + additionalFields），
     * 替代 Phase A 中仅 id + vector 两个字段的最小 Schema。这样后续
     * addEmbeddings / searchByVector 才能直接复用 content / metadata 字段。</p>
     *
     * <p><b>注意：</b>如果Collection已存在，该方法会抛出异常。
     * 调用前建议先使用{@link #indexExists(String)}检查Collection是否存在。</p>
     *
     * @param indexName 集合名称，用于唯一标识一个Collection
     * @param config    索引配置，包含维度、分片数、索引类型、度量类型等参数
     * @throws RuntimeException 如果创建Collection或索引失败
     * @see #indexExists(String)
     */
    @Override
    protected void createIndexImpl(String indexName, VectorProperties.IndexConfig config) {
        // 复用完整字段构造逻辑：id + vector + content + metadata + additionalFields
        List<FieldType> fields = buildFieldTypes(config);

        // 构建Collection创建参数
        // enableDynamicField=true允许Collection接受未定义的额外字段
        CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                .withCollectionName(indexName)
                .withDescription("Vector collection")
                // 分片数控制数据分布，分片越多并行度越高但开销也越大
                // 默认1个分片，适合大多数场景
                .withShardsNum(config.getShards() != null ? config.getShards() : 1)
                .withSchema(CollectionSchemaParam.newBuilder()
                        .withFieldTypes(fields)
                        .withEnableDynamicField(true)
                        .build())
                .build();

        // 执行Collection创建
        R<RpcStatus> resp = milvusClient.createCollection(createParam);
        if (resp.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Milvus createCollection failed: %s".formatted(resp.getMessage()));
        }

        // 构建索引参数
        // 索引类型和度量类型从配置中获取，未指定则使用默认值
        Map<String, String> extraParams = new HashMap<>();
        if (config.getIndexParams() != null) {
            // 将索引参数（如M、efConstruction等）转换为JSON字符串
            // 这些参数对HNSW等索引类型的性能影响很大
            config.getIndexParams().forEach((k, v) -> extraParams.put(k, v.toString()));
        }

        CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                .withCollectionName(indexName)
                .withFieldName("vector")
                // Milvus enum 是大写 HNSW/IVF_FLAT/COSINE/IP，但 VectorProperties.IndexConfig 默认值是 "hnsw"/"cosine" 小写（Qdrant 等其他 provider 的约定）
                // 在这里归一化确保两个 provider 共享同一 IndexConfig 不撕裂
                .withIndexType(IndexType.valueOf((config.getIndexType() != null ? config.getIndexType() : milvusConfig.getIndexType().name()).toUpperCase()))
                // 度量类型决定如何计算向量之间的距离
                .withMetricType(MetricType.valueOf((config.getMetric() != null ? config.getMetric() : milvusConfig.getMetricType().name()).toUpperCase()))
                .withExtraParam(Objects.requireNonNull(JsonUtils.getInstance().serialize(extraParams)))
                // 同步模式：索引构建完成后才返回，确保索引立即可用
                .withSyncMode(true)
                .build();

        // 执行索引创建
        R<RpcStatus> indexResp = milvusClient.createIndex(indexParam);
        if (indexResp.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Milvus createIndex failed: %s".formatted(indexResp.getMessage()));
        }

        // 加载 Collection 到内存 — 否则后续 query/search 会报 "collection not loaded"
        milvusClient.loadCollection(LoadCollectionParam.newBuilder()
                .withCollectionName(indexName)
                .build());
    }

    /**
     * 判断指定集合（Collection）是否存在
     *
     * <p>通过列出所有Collection并检查目标Collection是否在其中来判断存在性。</p>
     *
     * @param indexName 集合名称
     * @return 如果集合存在返回true，否则返回false
     * @throws RuntimeException 如果查询Collection列表失败
     */
    @Override
    protected boolean indexExistsImpl(String indexName) {
        // hasCollection() 是 O(1) 操作，直接查询目标 Collection 是否存在
        // 优于 showCollections() 再 contains() 的 O(n) 方案
        HasCollectionParam param = HasCollectionParam.newBuilder()
                .withCollectionName(indexName)
                .build();
        R<Boolean> resp = milvusClient.hasCollection(param);
        if (resp.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Milvus hasCollection failed: %s".formatted(resp.getMessage()));
        }
        return resp.getData();
    }

    /**
     * 获取指定集合（Collection）的配置信息
     *
     * <p>通过描述Collection获取其结构信息，包括分片数、字段定义等。</p>
     *
     * <p><b>注意：</b>该方法只能获取部分配置信息，如需完整配置请在创建时保存。</p>
     *
     * @param indexName 集合名称
     * @return IndexConfig对象，包含集合的配置信息；如果集合不存在返回null
     * @throws RuntimeException 如果描述Collection失败
     */
    @Override
    protected VectorProperties.IndexConfig getIndexConfigImpl(String indexName) {
        DescribeCollectionParam param = DescribeCollectionParam.newBuilder().withCollectionName(indexName).build();
        R<DescribeCollectionResponse> resp = milvusClient.describeCollection(param);
        if (resp.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Milvus describeCollection failed: %s".formatted(resp.getMessage()));
        }

        VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
        config.setName(indexName);
        config.setShards(resp.getData().getShardsNum());

        // 遍历 schema 中的字段，提取向量字段的维度（从 type_params.dim 取）
        for (io.milvus.grpc.FieldSchema field : resp.getData().getSchema().getFieldsList()) {
            if ("vector".equals(field.getName())) {
                for (io.milvus.grpc.KeyValuePair kv : field.getTypeParamsList()) {
                    if ("dim".equals(kv.getKey())) {
                        config.setDimension(Integer.parseInt(kv.getValue()));
                        break;
                    }
                }
                break;
            }
        }
        // 索引类型和度量类型需要额外查询索引信息才能获取，此处暂不处理
        return config;
    }

    /**
     * 统计指定集合（Collection）下的文档数量
     *
     * <p>通过获取Collection的统计信息来计算文档数量。</p>
     *
     * @param indexName 集合名称
     * @return 文档数量，如果集合不存在或查询失败返回0
     * @throws RuntimeException 如果获取统计信息失败
     */
    @Override
    protected long countDocumentsImpl(String indexName) {
        // 先用 getCollectionStatistics —— 对 unloaded collection 也工作（query 在 unloaded 时报 "collection not loaded"）
        // 当 row_count > 0 但实际已被 delete 完时（Milvus row_count 在 delete 后不更新），回退到 query API 实时数
        long statCount = countViaStatistics(indexName);
        if (statCount == 0L) {
            return 0L;
        }
        return countViaQuery(indexName, statCount);
    }

    private long countViaQuery(String indexName, long fallbackCount) {
        QueryParam qp = QueryParam.newBuilder()
                .withCollectionName(indexName)
                .withExpr("id != \"\"")
                .withOutFields(List.of("id"))
                .withLimit(10000L)
                .build();
        try {
            R<QueryResults> resp = milvusClient.query(qp);
            if (resp.getStatus() != R.Status.Success.getCode()) {
                if (resp.getMessage() != null && resp.getMessage().toLowerCase().contains("not found")) {
                    return 0L;
                }
                throw new RuntimeException("Milvus countDocuments failed: " + resp.getMessage());
            }
            return new QueryResultsWrapper(resp.getData()).getRowRecords().size();
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("not loaded")) {
                return fallbackCount;
            }
            throw e;
        }
    }

    private long countViaStatistics(String indexName) {
        GetCollectionStatisticsParam param = GetCollectionStatisticsParam.newBuilder()
                .withCollectionName(indexName)
                .build();
        R<GetCollectionStatisticsResponse> resp = milvusClient.getCollectionStatistics(param);
        if (resp.getStatus() != R.Status.Success.getCode()) {
            if (resp.getMessage() != null && resp.getMessage().toLowerCase().contains("not found")) {
                return 0L;
            }
            throw new RuntimeException("Milvus getCollectionStatistics failed: " + resp.getMessage());
        }
        for (KeyValuePair stat : resp.getData().getStatsList()) {
            if ("row_count".equals(stat.getKey())) {
                return Long.parseLong(stat.getValue());
            }
        }
        return 0L;
    }

    /**
     * 删除指定的 Milvus Collection。
     *
     * @param indexName Collection 名称
     * @throws RuntimeException 删除 Collection 失败时抛出
     */
    @Override
    protected void deleteIndexImpl(String indexName) {
        DropCollectionParam param = DropCollectionParam.newBuilder()
                .withCollectionName(indexName)
                .build();
        R<RpcStatus> resp = milvusClient.dropCollection(param);
        if (resp.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Milvus dropCollection failed: %s".formatted(resp.getMessage()));
        }
        log.info("Milvus Collection [{}] 删除完成", indexName);
    }

    /**
     * 列出 Milvus 中的全部 Collection。
     *
     * @return Collection 索引信息列表
     * @throws RuntimeException 查询 Collection 列表失败时抛出
     */
    @Override
    protected List<IndexInfo> listIndexesImpl() {
        ShowCollectionsParam param = ShowCollectionsParam.newBuilder().build();
        R<ShowCollectionsResponse> resp = milvusClient.showCollections(param);
        if (resp.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Milvus showCollections failed: %s".formatted(resp.getMessage()));
        }
        List<IndexInfo> indexes = resp.getData().getCollectionNamesList().stream()
                .map(this::describeIndexImpl)
                .toList();
        log.debug("Milvus Collection 列表查询完成，数量={}", indexes.size());
        return indexes;
    }

    /**
     * 获取指定 Collection 的配置与文档统计信息。
     *
     * @param indexName Collection 名称
     * @return Collection 描述信息
     * @throws RuntimeException 查询 Collection 配置或统计信息失败时抛出
     */
    @Override
    protected IndexInfo describeIndexImpl(String indexName) {
        VectorProperties.IndexConfig config = getIndexConfigImpl(indexName);
        long count = countDocumentsImpl(indexName);
        int shardsNum = config.getShards() != null ? config.getShards() : 1;
        int dimension = config.getDimension() != null ? config.getDimension() : DEFAULT_DIMENSION;
        String metric = config.getMetric() != null
                ? config.getMetric()
                : milvusConfig.getMetricType().name().toLowerCase(Locale.ROOT);
        String indexType = config.getIndexType() != null
                ? config.getIndexType()
                : milvusConfig.getIndexType().name().toLowerCase(Locale.ROOT);
        return new IndexInfo(indexName, Modality.TEXT, dimension, metric, indexType,
                IndexStatus.READY, count, null, null, Map.of("shardsNum", shardsNum));
    }

    /**
     * 尝试在线更新 Collection 索引配置。
     *
     * <p>Milvus 不支持在线修改向量索引参数，需删除并重建 Collection。</p>
     *
     * @param indexName Collection 名称
     * @param config    新索引配置
     * @return 始终返回 false，表示未执行在线更新
     */
    @Override
    protected boolean updateIndexConfigImpl(String indexName, VectorProperties.IndexConfig config) {
        log.warn("Milvus Collection [{}] 不支持在线修改索引配置，需删除后重建", indexName);
        return false;
    }

    /**
     * 获取指定 Collection 的运行统计信息。
     *
     * @param indexName Collection 名称
     * @return 包含文档数、分片数、维度和度量方式的统计信息
     * @throws RuntimeException 查询 Collection 配置或统计信息失败时抛出
     */
    @Override
    protected IndexInfo getIndexStatsImpl(String indexName) {
        VectorProperties.IndexConfig config = getIndexConfigImpl(indexName);
        long count = countDocumentsImpl(indexName);
        int shardsNum = config.getShards() != null ? config.getShards() : 1;
        int dimension = config.getDimension() != null ? config.getDimension() : DEFAULT_DIMENSION;
        String metric = config.getMetric() != null
                ? config.getMetric()
                : milvusConfig.getMetricType().name().toLowerCase(Locale.ROOT);
        String indexType = config.getIndexType() != null
                ? config.getIndexType()
                : milvusConfig.getIndexType().name().toLowerCase(Locale.ROOT);
        log.debug("Milvus Collection [{}] 统计完成，文档数={}，分片数={}", indexName, count, shardsNum);
        return new IndexInfo(indexName, Modality.TEXT, dimension, metric, indexType,
                IndexStatus.READY, count, null, null, Map.of("shardsNum", shardsNum));
    }

    // ========== 私有辅助方法 ==========

    private double distanceToSimilarity(double distance, MetricType metricType) {
        double score;
        switch (metricType) {
            case IP -> score = distance;
            case L2 -> score = 1.0 / (1.0 + distance);
            default -> score = 1.0 - distance;
        }
        return Math.clamp(score, 0.0, 1.0);
    }

    /**
     * 构建字段类型列表
     *
     * <p>根据配置构建完整的字段类型列表，包括：</p>
     * <ul>
     *   <li>主键字段（id）：用于唯一标识文档</li>
     *   <li>向量字段（vector）：存储文档的向量表示</li>
     *   <li>内容字段（content）：存储文档的原始文本</li>
     *   <li>元数据字段（metadata）：存储文档的元信息（JSON格式）</li>
     *   <li>额外字段（additional）：从配置中动态添加的字段</li>
     * </ul>
     *
     * @param config 索引配置，包含维度、额外字段等信息
     * @return 字段类型列表，用于构建Collection的Schema
     */
    private List<FieldType> buildFieldTypes(VectorProperties.IndexConfig config) {
        List<FieldType> fieldTypes = new ArrayList<>();

        // 1. 主键字段（必需）
        // 使用VarChar类型，允许更灵活的主键格式
        // maxLength=65535支持较长的主键字符串
        FieldType idField = FieldType.newBuilder()
                .withName("id")
                .withDescription("Primary key field")
                .withDataType(DataType.VarChar)
                .withMaxLength(65535)
                .withPrimaryKey(true)
                .withAutoID(false)
                .build();
        fieldTypes.add(idField);

        // 2. 向量字段（必需）
        // FloatVector类型是Milvus推荐的向量存储类型
        // 维度必须与嵌入模型输出维度一致，否则搜索会失败
        FieldType vectorField = FieldType.newBuilder()
                .withName("vector")
                .withDescription("Vector field")
                .withDataType(DataType.FloatVector)
                .withDimension(config.getDimension() != null ? config.getDimension() : milvusConfig.getEmbeddingDimension())
                .build();
        fieldTypes.add(vectorField);

        // 3. 内容字段（必需）
        // 存储文档的原始文本内容，用于展示搜索结果
        FieldType contentField = FieldType.newBuilder()
                .withName("content")
                .withDescription("Document content")
                .withDataType(DataType.VarChar)
                .withMaxLength(65535)
                .build();
        fieldTypes.add(contentField);

        // 4. 元数据字段（必需）—— JSON 序列化后存为 VarChar，与 content 字段同形
        FieldType metadataField = FieldType.newBuilder()
                .withName("metadata")
                .withDescription("Document metadata as JSON string")
                .withDataType(DataType.VarChar)
                .withMaxLength(65535)
                .build();
        fieldTypes.add(metadataField);

        // 5. 动态字段（可选）
        // 从配置中读取额外字段，允许用户自定义扩展字段
        if (config.getAdditionalFields() != null) {
            for (Map.Entry<String, Object> entry : config.getAdditionalFields().entrySet()) {
                FieldType additionalField = buildAdditionalFieldType(entry.getKey(), entry.getValue());
                fieldTypes.add(additionalField);
            }
        }

        return fieldTypes;
    }

    /**
     * 构建额外字段类型
     *
     * <p>根据配置动态构建额外字段的类型定义。支持多种数据类型：</p>
     * <ul>
     *   <li>VarChar：字符串类型，需要指定maxLength</li>
     *   <li>Int64：整数类型，可作为主键</li>
     *   <li>FloatVector：向量类型，需要指定dim（维度）</li>
     * </ul>
     *
     * @param fieldName   字段名称，必须唯一
     * @param fieldConfig 字段配置，可以是简单字符串（默认VarChar）或复杂Map结构
     * @return 字段类型定义，用于添加到Collection Schema
     */
    private FieldType buildAdditionalFieldType(String fieldName, Object fieldConfig) {
        FieldType.Builder builder = FieldType.newBuilder()
                .withName(fieldName);

        if (fieldConfig instanceof Map) {
            // 复杂配置格式，支持完整字段定义
            @SuppressWarnings("unchecked")
            Map<String, Object> config = (Map<String, Object>) fieldConfig;

            builder.withDescription((String) config.getOrDefault("description", "Additional field"));

            // 从配置中获取数据类型，默认为VarChar
            String dataType = (String) config.getOrDefault("data_type", "VarChar");
            builder.withDataType(DataType.valueOf(dataType));

            // 根据数据类型设置特定属性
            if ("VarChar".equals(dataType)) {
                // 字符串类型需要指定最大长度
                builder.withMaxLength((Integer) config.getOrDefault("max_length", 65535));
            } else if ("Int64".equals(dataType)) {
                // 整数类型可以标记为主键
                builder.withPrimaryKey((Boolean) config.getOrDefault("is_primary", false));
            } else if ("FloatVector".equals(dataType)) {
                // 向量类型必须指定维度
                builder.withDimension((Integer) config.get("dim"));
            }

        } else {
            // 简单字符串配置，默认为VarChar类型
            builder.withDescription("Additional field")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(65535);
        }

        return builder.build();
    }

    /**
     * 根据向量进行相似度搜索
     *
     * <p>该方法接收一个查询向量，在指定的Collection中查找最相似的文档。
     * 使用Milvus的向量搜索功能，基于余弦相似度或其他度量方式计算相似度。</p>
     *
     * <p><b>算法流程：</b></p>
     * <ol>
     *   <li>参数校验：检查向量和limit的有效性</li>
     *   <li>构建搜索参数：指定Collection名称、向量字段、返回字段等</li>
     *   <li>执行搜索：调用Milvus客户端进行向量搜索</li>
     *   <li>解析结果：将Milvus返回的原始数据转换为VectorSearchResult对象</li>
     *   <li>计算相似度分数：将距离转换为相似度分数（1-distance）</li>
     * </ol>
     *
     * <p><b>距离与相似度的转换：</b>
     * 对于余弦相似度，距离越小相似度越高，因此分数 = 1 - distance。
     * 分数会被裁剪到[0, 1]范围内。</p>
     *
     * @param indexName   索引名称
     * @return 搜索结果列表，按相似度降序排列
     * @throws IllegalArgumentException 如果queryVector为空或limit小于等于0
     * @throws RuntimeException 如果搜索执行失败
     */
    @Override
    protected long truncateIndexImpl(String indexName) {
        long previousCount = countDocuments(indexName);
        DeleteParam deleteParam = DeleteParam.newBuilder()
                .withCollectionName(indexName)
                .withExpr("id != \"\"")
                .build();
        R<MutationResult> resp = milvusClient.delete(deleteParam);
        if (resp.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Milvus truncateIndex failed: " + resp.getMessage());
        }

        // flush 后 row_count 才会反映删除结果，与 addEmbeddings 后的 flush 对称
        milvusClient.flush(FlushParam.newBuilder().withCollectionNames(List.of(indexName)).build());
        long deleted = resp.getData() != null ? resp.getData().getDeleteCnt() : previousCount;
        log.info("Milvus 清空 collection [{}] 完成，预估={}, 删除={}", indexName, previousCount, deleted);
        return Math.max(deleted, previousCount);
    }

    @Override
    protected List<org.springframework.ai.document.Document> similaritySearchByVector(String indexName, float[] queryVector,
                                                                                        int limit, double minScore) {
        if (queryVector == null || queryVector.length == 0) {
            throw new IllegalArgumentException("查询向量不能为空");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit 必须大于 0");
        }

        // Milvus SDK要求Float类型而非float原始类型，pre-allocate容量避免扩容
        List<Float> vectorList = new ArrayList<>(queryVector.length);
        for (float v : queryVector) vectorList.add(v);

        // 构建搜索参数
        // withCollectionName: 目标Collection名称（Milvus中表的概念）
        // withVectorFieldName: 向量字段名，必须与Collection定义的字段名一致
        // withFloatVectors: 查询向量（必须是Float列表）
        // withTopK: 返回最近邻的数量，等同于limit
        // withMetricType: 距离度量类型（COSINE/L2/IP），决定如何计算相似度
        // withOutFields: 指定返回哪些字段（排除vector字段节省带宽）
        SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(indexName)
                .withVectorFieldName("vector")
                .withFloatVectors(Collections.singletonList(vectorList))
                .withLimit((long) limit)
                .withMetricType(milvusConfig.getMetricType())
                .withOutFields(SEARCH_OUTPUT_FIELDS)
                .build();

        // 调用Milvus客户端执行搜索，返回SearchResults
        R<SearchResults> response = milvusClient.search(searchParam);
        // R<SearchResults>是Milvus SDK的统一响应封装，getStatus()返回状态码
        if (response.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Milvus search failed: " + response.getMessage());
        }

        // 解析搜索结果
        // response.getData()获取SearchResults，其中getResults()返回SearchResultData（protobuf消息）
        SearchResults results = response.getData();
        // SearchResultsWrapper是Milvus SDK提供的结果解析工具类，封装了protobuf数据的读取逻辑
        SearchResultsWrapper wrapper = new SearchResultsWrapper(results.getResults());
        List<Document> searchResults = new ArrayList<>();

        // getIDScore(0)获取第一批（index=0）的ID-分数对列表
        // IDScore是Milvus SDK的内部类，包含：longID、strID（主键的两种类型）、score（距离值）
        List<SearchResultsWrapper.IDScore> idScores = wrapper.getIDScore(0);

        // 检查content字段是否存在，避免用异常做控制流
        // 通过遍历fieldsDataList判断字段是否存在
        List<?> contentData = null;
        boolean hasContentField = results.getResults().getFieldsDataList().stream()
                .anyMatch(f -> "content".equals(f.getFieldName()));
        if (hasContentField) {
            contentData = wrapper.getFieldData("content", 0);
        }

        // 遍历每条搜索结果，提取id、content、score组成VectorSearchResult
        for (int i = 0; i < idScores.size(); i++) {
            SearchResultsWrapper.IDScore idScore = idScores.get(i);

            // Milvus主键可能是long类型或string类型，根据实际类型取值
            // longID!=0表示使用的是long型主键，否则使用strID
            String id = idScore.getLongID() != 0 ? String.valueOf(idScore.getLongID()) : idScore.getStrID();

            // 从contentData列表中取出对应索引的content值
            String content = "";
            if (contentData != null && i < contentData.size()) {
                Object c = contentData.get(i);
                if (c != null) {
                    content = c.toString();
                }
            }

            // Milvus返回的是distance（距离），需要转换为similarity（相似度）
            // 转换规则取决于度量类型：COSINE/IP时similarity=distance；L2时similarity=1/(1+distance)
            double score = distanceToSimilarity(idScore.getScore(), milvusConfig.getMetricType());

            if (score >= minScore) {
                searchResults.add(new Document(id, content, Map.of("score", score)));
            }
        }

        return searchResults;
    }

    @Override
    protected void addEmbeddings(String indexName, List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return;
        }

        List<String> ids = docs.stream().map(Document::getId).toList();
        List<String> contents = docs.stream().map(d -> d.getText() != null ? d.getText() : "").toList();
        List<List<Float>> vectors = new ArrayList<>();
        List<String> metadataList = new ArrayList<>();

        for (Document doc : docs) {
            float[] embedding = (float[]) doc.getMetadata().get("embedding");
            List<Float> vectorList = new ArrayList<>(embedding.length);
            for (float v : embedding) vectorList.add(v);
            vectors.add(vectorList);

            try {
                metadataList.add(JsonUtils.getInstance().serialize(doc.getMetadata()));
            } catch (Exception e) {
                metadataList.add("{}");
            }
        }

        List<InsertParam.Field> fields = Arrays.asList(
                new InsertParam.Field("id", ids),
                new InsertParam.Field("vector", vectors),
                new InsertParam.Field("content", contents),
                new InsertParam.Field("metadata", metadataList)
        );

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(indexName)
                .withFields(fields)
                .build();

        R<MutationResult> resp = milvusClient.insert(insertParam);
        if (resp.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Milvus insert failed: " + resp.getMessage());
        }

        // flush 强制将 buffer 数据写入 sealed segment，否则后续 countDocuments/getByIds 读不到 row_count/数据
        milvusClient.flush(FlushParam.newBuilder().withCollectionNames(List.of(indexName)).build());
        log.info("Milvus 批量写入 Collection [{}] 完成，文档数={}", indexName, docs.size());
    }

    @Override
    protected void deleteByIds(String indexName, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        String expr = "id in [" + ids.stream()
                .map(id -> "\"" + id + "\"")
                .collect(Collectors.joining(",")) + "]";

        DeleteParam deleteParam = DeleteParam.newBuilder()
                .withCollectionName(indexName)
                .withExpr(expr)
                .build();

        R<MutationResult> resp = milvusClient.delete(deleteParam);
        if (resp.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Milvus deleteByIds failed: " + resp.getMessage());
        }

        milvusClient.flush(FlushParam.newBuilder().withCollectionNames(List.of(indexName)).build());
        log.debug("Milvus 删除 Collection [{}] 文档，ID数={}", indexName, ids.size());
    }

    @Override
    protected List<VectorRecord> getByIds(String indexName, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        String expr = "id in [" + ids.stream()
                .map(id -> "\"" + id + "\"")
                .collect(Collectors.joining(",")) + "]";

        QueryParam queryParam = QueryParam.newBuilder()
                .withCollectionName(indexName)
                .withExpr(expr)
                .withOutFields(List.of("id", "content"))
                .build();

        R<QueryResults> resp = milvusClient.query(queryParam);
        if (resp.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Milvus query failed: " + resp.getMessage());
        }

        QueryResultsWrapper wrapper = new QueryResultsWrapper(resp.getData());
        List<QueryResultsWrapper.RowRecord> records = wrapper.getRowRecords();
        List<VectorRecord> result = new ArrayList<>(records.size());
        for (QueryResultsWrapper.RowRecord record : records) {
            String id = (String) record.get("id");
            Object contentObj = record.get("content");
            String content = contentObj != null ? contentObj.toString() : "";
            if (content.isBlank()) {
                log.warn("Milvus getByIds: record id={} content 为空，跳过（可能是 dynamic field 路径或 schema 偏差）", id);
                continue;
            }
            // 显式 setId(id) —— 不能用 VectorRecord.text(idx,id,content) 形式（与 text(idx,text,metadata) 重载歧义）
            result.add(VectorRecord.text(indexName, content).setId(id));
        }
        return result;
    }

    @Override
    protected List<VectorRecord> listDocumentsImpl(String indexName, int offset, int limit) {
        return listDocumentsHandler(indexName, offset, limit);
    }

    protected List<VectorRecord> listDocumentsHandler(String indexName, int offset, int limit) {
        QueryParam queryParam = QueryParam.newBuilder()
                .withCollectionName(indexName)
                .withExpr("")
                .withOutFields(List.of("id", "content"))
                .withOffset((long) offset)
                .withLimit((long) limit)
                .build();

        R<QueryResults> resp = milvusClient.query(queryParam);
        if (resp.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Milvus listDocuments failed: " + resp.getMessage());
        }

        QueryResultsWrapper wrapper = new QueryResultsWrapper(resp.getData());
        List<QueryResultsWrapper.RowRecord> records = wrapper.getRowRecords();
        List<VectorRecord> docs = new ArrayList<>(records.size());
        for (QueryResultsWrapper.RowRecord record : records) {
            String id = (String) record.get("id");
            Object contentObj = record.get("content");
            String content = contentObj != null ? contentObj.toString() : "";
            docs.add(VectorRecord.text(indexName, content).setId(id));
        }
        return docs;
    }

    // ====================================================================
    // §14.4 运维 / 别名 / 备份 — Milvus 实现
    // Milvus SDK V1 client (MilvusServiceClient) 提供 manualCompact / createAlias / alterAlias 原生能力；
    // backup / restore 通过 milvus-backup 工具（out-of-process）提供，SDK 不直接暴露。
    // ====================================================================

    /**
     * 触发 Milvus Collection 数据压缩（manualCompact），合并已删除/过期的小 Segment 以释放磁盘并提升查询性能。
     *
     * <p>对应 Milvus gRPC {@code ManualCompaction} 命令，返回 {@link ManualCompactionResponse}。
     * Status 成功表示压缩任务已下发（异步执行），不代表已结束，调用方可继续以
     * {@code resp.getData().getCompactionID()} 轮询进度。</p>
     *
     * @param indexName Collection 名称
     * @return true 表示 SDK 调用成功接收任务；false 表示 Status 非 Success 或抛出异常
     */
    @Override
    protected boolean optimizeImpl(String indexName) {
        try {
            R<ManualCompactionResponse> resp = milvusClient.manualCompact(
                    ManualCompactParam.newBuilder()
                            .withCollectionName(indexName)
                            .build());
            return resp.getStatus() == R.Status.Success.getCode();
        } catch (Exception e) {
            log.warn("Milvus optimize failed for [{}]: {}", indexName, e.getMessage());
            return false;
        }
    }

    /**
     * 为 Collection 创建别名（alias）。一个 Collection 可拥有多个 alias，alias 用于 zero-downtime 切换。
     *
     * <p>对应 Milvus gRPC {@code CreateAlias} 命令，返回 {@link RpcStatus}。</p>
     *
     * @param indexName Collection 名称
     * @param alias     别名
     * @return true 表示创建成功；false 表示 Status 非 Success 或抛出异常
     */
    @Override
    protected boolean createAliasImpl(String indexName, String alias) {
        try {
            R<RpcStatus> resp = milvusClient.createAlias(
                    CreateAliasParam.newBuilder()
                            .withCollectionName(indexName)
                            .withAlias(alias)
                            .build());
            return resp.getStatus() == R.Status.Success.getCode();
        } catch (Exception e) {
            log.warn("Milvus createAlias failed: collection={}, alias={}, error={}",
                    indexName, alias, e.getMessage());
            return false;
        }
    }

    /**
     * 原子地将 alias 从旧 Collection 切换到新 Collection。
     *
     * <p>Milvus SDK 使用 {@code alterAlias(AlterAliasParam)} 实现切换：传入新的 Collection 名
     * 和 alias，Milvus 会保证操作的原子性。返回 {@link RpcStatus}。</p>
     *
     * <p>注意：{@code oldIndexName} 参数在 SDK 的 {@code AlterAliasParam} 中不被使用，
     * 因为 alterAlias 本身就是「将该 alias 重定向到新 Collection」的语义；但本方法签名
     * 仍保留以匹配抽象层契约，参数校验在调用方完成。</p>
     *
     * @param oldIndexName 原 Collection 名称（SDK 不使用）
     * @param newIndexName 新 Collection 名称
     * @param alias        别名
     * @return true 表示切换成功；false 表示 Status 非 Success 或抛出异常
     */
    @Override
    protected boolean switchAliasImpl(String oldIndexName, String newIndexName, String alias) {
        try {
            R<RpcStatus> resp = milvusClient.alterAlias(
                    AlterAliasParam.newBuilder()
                            .withCollectionName(newIndexName)
                            .withAlias(alias)
                            .build());
            return resp.getStatus() == R.Status.Success.getCode();
        } catch (Exception e) {
            log.warn("Milvus switchAlias failed: old={}, new={}, alias={}, error={}",
                    oldIndexName, newIndexName, alias, e.getMessage());
            return false;
        }
    }

    /**
     * 备份 Collection — Milvus SDK 不提供原生 backup API，备份由 milvus-backup 工具 out-of-process 完成。
     *
     * <p>VectorService 抽象层对所有 provider 提供统一的 backup 契约；Milvus 实现层不直接支持，
     * 应由运维侧通过 {@code milvus-backup} CLI / REST 接口完成。</p>
     *
     * @param indexName  Collection 名称
     * @param targetPath 备份目标路径（MinIO / S3 等），SDK 不使用
     * @throws UnsupportedOperationException 始终抛出，明确告知调用方使用 milvus-backup 工具
     */
    @Override
    protected boolean backupImpl(String indexName, String targetPath) {
        return throwUnsupportedOps("backup", indexName, "milvus");
    }

    /**
     * 从备份恢复 Collection — Milvus SDK 不提供原生 restore API，同 backup 一致由 milvus-backup 工具承担。
     *
     * @param sourcePath 备份源路径（SDK 不使用）
     * @param indexName  目标 Collection 名称
     * @throws UnsupportedOperationException 始终抛出，明确告知调用方使用 milvus-backup 工具
     */
    @Override
    protected boolean restoreImpl(String sourcePath, String indexName) {
        return throwUnsupportedOps("restore", indexName, "milvus");
    }

}