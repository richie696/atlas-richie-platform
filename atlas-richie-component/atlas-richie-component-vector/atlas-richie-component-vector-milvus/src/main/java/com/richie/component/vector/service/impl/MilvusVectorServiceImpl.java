package com.richie.component.vector.service.impl;

import com.richie.context.utils.data.JsonUtils;
import com.richie.component.vector.config.MilvusConfig;
import com.richie.component.vector.config.VectorProperties;
import com.richie.component.vector.model.VectorDocument;
import com.richie.component.vector.model.VectorSearchResult;
import com.richie.component.vector.service.VectorService;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.*;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.*;
import io.milvus.param.dml.SearchParam;
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
 * @author Rydeen Platform Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "platform.component.vector", name = "provider", havingValue = "milvus")
public class MilvusVectorServiceImpl extends VectorServiceImpl implements VectorService {

    private final MilvusConfig milvusConfig;
    private final MilvusServiceClient milvusClient;

    private static final List<String> SEARCH_OUTPUT_FIELDS = List.of("id", "content");

    /**
     * 构造方法，注入Milvus客户端和配置
     *
     * <p>使用@Qualifier注解指定EmbeddingModel bean，确保注入正确的嵌入模型实例。</p>
     *
     * @param vectorStore    Spring AI VectorStore，用于文档存储和检索
     * @param embeddingModel 嵌入模型，用于将文本转换为向量
     * @param milvusConfig   Milvus配置，包含连接信息和默认参数
     * @param milvusClient   Milvus客户端，提供与Milvus服务的通信能力
     */
    @Autowired
    public MilvusVectorServiceImpl(VectorStore vectorStore,
                                   @Qualifier("aiEmbeddingModel") EmbeddingModel embeddingModel,
                                   MilvusConfig milvusConfig, MilvusServiceClient milvusClient) {
        super(vectorStore, embeddingModel);
        this.milvusConfig = milvusConfig;
        this.milvusClient = milvusClient;
    }

    /**
     * 创建Milvus集合（Collection），支持动态字段配置
     *
     * <p>该方法会创建一个新的Milvus Collection，包含主键字段和向量字段。
     * 集合创建后会自动创建相应的索引以加速向量搜索。</p>
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
    public void createIndex(String indexName, VectorProperties.IndexConfig config) {
        // 构建字段列表：主键字段 + 向量字段
        // Milvus要求每个Collection至少包含主键字段和向量字段
        List<FieldType> fields = new ArrayList<>();

        // 主键字段：使用Int64类型，支持自增ID
        // 主键用于唯一标识每条记录，是Milvus的必需字段
        fields.add(FieldType.newBuilder()
                .withName("id")
                .withDescription("Primary key")
                .withDataType(DataType.Int64)
                .withPrimaryKey(true)
                .withAutoID(false)
                .build());

        // 向量字段：存储文档的向量表示
        // 维度从配置中获取，如果未指定则使用milvusConfig中的默认值
        // 维度必须与嵌入模型输出的向量维度一致
        fields.add(FieldType.newBuilder()
                .withName("vector")
                .withDescription("Vector field")
                .withDataType(DataType.FloatVector)
                .withDimension(config.getDimension() != null ? config.getDimension() : milvusConfig.getEmbeddingDimension())
                .build());

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
                // 索引类型决定向量搜索的算法：HNSW（推荐）、IVF、FLAT等
                .withIndexType(config.getIndexType() != null ? IndexType.valueOf(config.getIndexType()) : milvusConfig.getIndexType())
                // 度量类型决定如何计算向量之间的距离
                .withMetricType(config.getMetric() != null ? MetricType.valueOf(config.getMetric()) : milvusConfig.getMetricType())
                .withExtraParam(Objects.requireNonNull(JsonUtils.getInstance().serialize(extraParams)))
                // 同步模式：索引构建完成后才返回，确保索引立即可用
                .withSyncMode(true)
                .build();

        // 执行索引创建
        R<RpcStatus> indexResp = milvusClient.createIndex(indexParam);
        if (indexResp.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Milvus createIndex failed: %s".formatted(indexResp.getMessage()));
        }
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
    public boolean indexExists(String indexName) {
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
    public VectorProperties.IndexConfig getIndexConfig(String indexName) {
        DescribeCollectionParam param = DescribeCollectionParam.newBuilder().withCollectionName(indexName).build();
        R<DescribeCollectionResponse> resp = milvusClient.describeCollection(param);
        if (resp.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Milvus describeCollection failed: %s".formatted(resp.getMessage()));
        }

        VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
        config.setName(indexName);
        config.setShards(resp.getData().getShardsNum());

        // 遍历所有字段，提取向量字段的维度信息
        // 找到后立即break，避免冗余迭代
        DescribeCollectionResponse data = resp.getData();
        for (Object obj : data.getAllFields().values()) {
            FieldType field = (FieldType) obj;
            if ("vector".equals(field.getName())) {
                // 提取向量维度，这是搜索配置的关键参数
                config.setDimension(field.getDimension());
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
    public long countDocuments(String indexName) {
        GetCollectionStatisticsParam param = GetCollectionStatisticsParam.newBuilder()
                .withCollectionName(indexName)
                .build();
        R<GetCollectionStatisticsResponse> resp = milvusClient.getCollectionStatistics(param);
        if (resp.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Milvus getCollectionStatistics failed: %s".formatted(resp.getMessage()));
        }

        // 遍历统计信息列表，查找row_count字段
        // row_count表示Collection中的文档数量
        for (KeyValuePair stat : resp.getData().getStatsList()) {
            if ("row_count".equals(stat.getKey())) {
                return Long.parseLong(stat.getValue());
            }
        }
        return 0L;
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

        // 4. 元数据字段（必需）
        // 使用JSON类型存储灵活的元数据，如来源、创建时间等
        FieldType metadataField = FieldType.newBuilder()
                .withName("metadata")
                .withDescription("Document metadata")
                .withDataType(DataType.JSON)
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
     * @param queryVector 查询向量，不能为空且维度必须与索引配置一致
     * @param limit       返回结果数量，必须大于0
     * @return 搜索结果列表，按相似度降序排列
     * @throws IllegalArgumentException 如果queryVector为空或limit小于等于0
     * @throws RuntimeException 如果搜索执行失败
     */
    @Override
    public List<VectorSearchResult> searchByVector(String indexName, float[] queryVector, int limit) {
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
        List<VectorSearchResult> searchResults = new ArrayList<>();

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

            searchResults.add(VectorSearchResult.of(id, content, score, null));
        }

        return searchResults;
    }

    @Override
    protected List<VectorDocument> listDocumentsHandler(String indexName, int offset, int limit) {
        throw new UnsupportedOperationException("Milvus listDocuments未实现");
    }

}