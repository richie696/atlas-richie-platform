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

import com.richie.context.utils.data.Collections;
import com.richie.component.vector.config.VectorProperties;
import com.richie.component.vector.model.IndexInfo;
import com.richie.component.vector.model.IndexStatus;
import com.richie.component.vector.model.Modality;
import com.richie.component.vector.model.VectorRecord;
import com.richie.component.vector.model.VectorSearchResult;
import com.richie.component.ai.service.RerankService;
import com.richie.component.vector.service.VectorService;
import com.google.common.util.concurrent.ListenableFuture;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Common;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.time.Instant;

/**
 * Qdrant向量数据库服务实现类.
 *
 * <p>基于Qdrant Java SDK提供高性能的向量存储和检索能力实现，
 * 实现统一的VectorService接口，提供与具体向量数据库无关的抽象层。</p>
 *
 * <p>该实现支持以下核心功能：</p>
 * <ul>
 *   <li>向量相似度搜索</li>
 *   <li>文档列表查询（分页滚动）</li>
 *   <li>文档计数</li>
 * </ul>
 *
 * <p><b>注意：</b>Qdrant作为托管型向量数据库，不支持传统意义上的索引管理操作
 * （创建/删除索引），因此相关方法会抛出UnsupportedOperationException。</p>
 *
 * @author richie696
 * @version 1.0.0-SNAPSHOT
 * @since 1.0.0
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "platform.component.vector", name = "provider", havingValue = "qdrant")
public class QdRantVectorServiceImpl extends AbstractVectorService implements VectorService {

    /** Qdrant客户端，用于与Qdrant服务通信 */
    private final QdrantClient qdrantClient;

    /** 异步操作等待超时时间（秒），防止无限期阻塞 */
    private static final int WAIT_TIMEOUT = 3;

    /**
     * 构造Qdrant向量服务实现.
     *
     * <p>通过构造器注入所需的依赖：VectorStore用于文档存储管理，
     * EmbeddingModel用于文本向量化，QdrantClient用于Qdrant服务通信。</p>
     *
     * @param rerankService 重排序服务（可选）
     * @param vectorStore Spring AI的VectorStore实现，用于文档的添加和删除
     * @param embeddingModel 嵌入模型，用于将文本转换为向量
     * @param qdrantClient Qdrant客户端，提供与Qdrant服务的低级API交互
     */
    @Autowired
    public QdRantVectorServiceImpl(@Autowired(required = false) RerankService rerankService,
                                   VectorStore vectorStore,
                                   @Qualifier("aiEmbeddingModel") EmbeddingModel embeddingModel,
                                   QdrantClient qdrantClient) {
        super(rerankService, vectorStore, embeddingModel);
        this.qdrantClient = qdrantClient;
    }

    /** Qdrant 默认向量维度（OpenAI text-embedding-3-small 等） */
    private static final int DEFAULT_DIMENSION = 1536;

    /**
     * 创建 Qdrant collection（Phase B 真实实现）。
     *
     * <p>使用 {@code createCollectionAsync} 同步等待完成。
     * VectorParams 携带维度 + 距离度量，HnswConfigDiff 配置 M=16 / efConstruct=128
     * （业界常用默认值，适合大多数通用场景）。</p>
     *
     * @param indexName collection 名称
     * @param config    索引配置（dimension / metric）
     */
    @Override
    protected void createIndexImpl(String indexName, VectorProperties.IndexConfig config) {
        int dimension = config != null && config.getDimension() != null ? config.getDimension() : DEFAULT_DIMENSION;
        String metric = config != null && config.getMetric() != null ? config.getMetric() : "cosine";

        io.qdrant.client.grpc.Collections.Distance distance = mapMetric(metric);

        io.qdrant.client.grpc.Collections.VectorParams vectorParams =
                io.qdrant.client.grpc.Collections.VectorParams.newBuilder()
                        .setSize(dimension)
                        .setDistance(distance)
                        .build();

        io.qdrant.client.grpc.Collections.HnswConfigDiff hnswConfig =
                io.qdrant.client.grpc.Collections.HnswConfigDiff.newBuilder()
                        .setM(16)
                        .setEfConstruct(128)
                        .build();

        io.qdrant.client.grpc.Collections.CreateCollection createCollection =
                io.qdrant.client.grpc.Collections.CreateCollection.newBuilder()
                        .setCollectionName(indexName)
                        .setVectorsConfig(io.qdrant.client.grpc.Collections.VectorsConfig.newBuilder()
                                .setParams(vectorParams).build())
                        .setHnswConfig(hnswConfig)
                        .build();

        try {
            qdrantClient.createCollectionAsync(createCollection).get(WAIT_TIMEOUT, TimeUnit.SECONDS);
            log.info("Qdrant collection 创建完成: name={}, dim={}, distance={}, hnswM=16, efConstruct=128",
                    indexName, dimension, distance);
        } catch (Exception e) {
            throw new RuntimeException("Qdrant createCollection failed: " + e.getMessage(), e);
        }
    }

    /**
     * 将 metric 字符串映射为 Qdrant gRPC Distance 枚举。
     * 未识别值默认 {@code Cosine}，与 provider 默认语义对齐。
     */
    private static io.qdrant.client.grpc.Collections.Distance mapMetric(String metric) {
        if (metric == null) {
            return io.qdrant.client.grpc.Collections.Distance.Cosine;
        }
        return switch (metric.toLowerCase()) {
            case "l2", "euclidean" -> io.qdrant.client.grpc.Collections.Distance.Euclid;
            case "ip", "dot" -> io.qdrant.client.grpc.Collections.Distance.Dot;
            default -> io.qdrant.client.grpc.Collections.Distance.Cosine;
        };
    }

    /**
     * 删除向量索引.
     *
     * <p><b>不支持的操作：</b>Qdrant的collection管理需要通过Qdrant的管理API进行，
     * 不通过客户端SDK直接删除。</p>
     *
     * @param indexName 索引名称（collection名称）
     * @throws UnsupportedOperationException 始终抛出
     */
    @Override
    protected void deleteIndexImpl(String indexName) {
        throw new UnsupportedOperationException("qdrant不支持索引功能");
    }

    /**
     * 检查索引是否存在.
     *
     * <p><b>不支持的操作：</b>Qdrant的collection存在性检查需要额外的API调用，
     * 当前实现不支持此功能。</p>
     *
     * @param indexName 索引名称（collection名称）
     * @return 无意义返回值
     * @throws UnsupportedOperationException 始终抛出
     */
    @Override
    protected boolean indexExistsImpl(String indexName) {
        try {
            return qdrantClient.listCollectionsAsync()
                    .get(WAIT_TIMEOUT, TimeUnit.SECONDS)
                    .stream()
                    .anyMatch(indexName::equals);
        } catch (Exception e) {
            log.warn("Qdrant collection existence check failed: name={}", indexName, e);
            return false;
        }
    }

    /**
     * 获取索引配置.
     *
     * <p><b>不支持的操作：</b>Qdrant的collection配置获取需要额外的管理API，
     * 当前实现不支持此功能。</p>
     *
     * @param indexName 索引名称（collection名称）
     * @return 无意义返回值
     * @throws UnsupportedOperationException 始终抛出
     */
    @Override
    protected VectorProperties.IndexConfig getIndexConfigImpl(String indexName) {
        try {
            io.qdrant.client.grpc.Collections.CollectionInfo collectionInfo = qdrantClient
                    .getCollectionInfoAsync(indexName)
                    .get(WAIT_TIMEOUT, TimeUnit.SECONDS);
            io.qdrant.client.grpc.Collections.VectorParams vectorParams = collectionInfo
                    .getConfig()
                    .getParams()
                    .getVectorsConfig()
                    .getParams();
            return new VectorProperties.IndexConfig()
                    .setName(indexName)
                    .setDimension(Math.toIntExact(vectorParams.getSize()))
                    .setMetric(vectorParams.getDistance().name().toLowerCase());
        } catch (Exception e) {
            log.warn("Qdrant collection config query failed: name={}", indexName, e);
            throw new RuntimeException("Qdrant getCollection failed: " + e.getMessage(), e);
        }
    }

    /**
     * 统计索引中的文档数量.
     *
     * <p>通过Qdrant的countAsync API获取指定collection中的文档总数。
     * 使用3秒超时防止无限期阻塞。</p>
     *
     * @param indexName collection名称
     * @return 文档数量，如果查询超时或出错则返回0
     */
    @Override
    protected long countDocumentsImpl(String indexName) {
        // 调用Qdrant异步count API获取文档计数
        var resp = qdrantClient.countAsync(indexName);
        Long count = 0L;
        try {
            // 等待异步结果，设置超时防止永久阻塞
            count = resp.get(WAIT_TIMEOUT, TimeUnit.SECONDS);
        } catch (Exception e) {
            // 记录超时错误但返回0，让调用方感知到查询失败
            log.error("获取值等待超时({})", WAIT_TIMEOUT);
        }
        return count;
    }

    /**
     * 分页列出索引中的文档.
     *
     * <p>使用Qdrant的scroll API进行分页查询，通过offset实现跳过已跳过的文档。
     * scroll API支持基于point ID的分页滚动，适合大规模数据遍历。</p>
     *
     * <p>实现逻辑：</p>
     * <ol>
     *   <li>循环调用scroll API，每次获取一批数据</li>
     *   <li>对于每批数据，先跳过offset指定的文档数</li>
     *   <li>将剩余文档转换为VectorRecord并添加到结果列表</li>
     *   <li>当结果数量达到limit时停止查询</li>
     *   <li>当返回空列表或没有下一页offset时说明数据已遍历完毕</li>
     * </ol>
     *
     * @param indexName collection名称
     * @param offset 跳过的文档数量，用于分页
     * @param limit 返回的最大文档数量
     * @return 文档列表，不足limit时可能少于指定数量
     */
    protected List<VectorRecord> listDocumentsImpl(String indexName, int offset, int limit) {
        return listDocumentsHandler(indexName, offset, limit);
    }

    protected List<VectorRecord> listDocumentsHandler(String indexName, int offset, int limit) {
        List<VectorRecord> docs = new ArrayList<>();
        Long lastId = null;
        int skipped = 0;

        // 循环分页获取文档，直到达到limit或数据遍历完毕
        while (docs.size() < limit) {
            // enable=true表示返回所有向量数据，使文档可被重新用于相似度计算
            Points.WithVectorsSelector withVectorsSelector = Points.WithVectorsSelector.newBuilder()
                    .setEnable(true) // true 表示返回所有向量
                    .build();

            // 构建scroll points请求，设置分页参数
            // offset使用上一个文档的ID实现游标分页，避免offset大时的性能问题
            var scoredPointsBuilder = Points.ScrollPoints.newBuilder()
                    .setCollectionName(indexName)
                    .setLimit(limit);
            if (lastId != null) {
                scoredPointsBuilder.setOffset(Common.PointId.newBuilder().setNum(lastId).build());
            }
            Points.ScrollPoints scoredPoints = scoredPointsBuilder
                    .setWithVectors(withVectorsSelector)
                    .build();

            // 异步执行scroll查询
            ListenableFuture<Points.ScrollResponse> resp = qdrantClient.scrollAsync(scoredPoints);

            Points.ScrollResponse scrollResponse;
            try {
                // 等待查询结果，设置超时防止永久阻塞
                scrollResponse = resp.get(WAIT_TIMEOUT, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("获取值等待超时({})", WAIT_TIMEOUT);
                throw new RuntimeException(e);
            }

            // 空结果列表表示已遍历完所有数据，无需继续
            if (scrollResponse.getResultList().isEmpty()) {
                break;
            }

            // 处理当前批次的每个point
            for (var point : scrollResponse.getResultList()) {
                // 跳过offset指定的文档数量，实现分页偏移
                if (skipped < offset) {
                    skipped++;
                    // 更新lastId确保即使跳过也能维护正确的分页游标
                    lastId = point.getId().getNum();
                    continue;
                }

                VectorRecord doc = VectorRecord.text(indexName, String.valueOf(point.getId().getNum()),
                        point.getPayloadMap().getOrDefault("content", JsonWithInt.Value.getDefaultInstance()).getStringValue());
                Map<String, Object> metadata = Collections.mapOf();
                point.getPayloadMap().forEach((key, value) -> metadata.put(key, value.getStringValue()));
                doc.setMetadata(metadata);
                docs.add(doc);

                // 达到limit后立即停止，不再处理更多数据
                if (docs.size() >= limit) {
                    break;
                }
            }

            // 检查是否还有更多数据需要遍历
            // hasNextPageOffset()返回null表示没有更多数据了
            // 或者已经达到limit也可以停止
            if (!scrollResponse.hasNextPageOffset() || docs.size() >= limit) {
                break;
            }
            // 获取下一页的offset游标，用于下一次scroll请求
            lastId = scrollResponse.getNextPageOffset().getNum();
        }
        return docs;
    }

    protected long truncateIndexImpl(String indexName) {
        long previousCount = countDocumentsImpl(indexName);
        try {
            qdrantClient.deleteAsync(indexName, Common.Filter.getDefaultInstance())
                    .get(WAIT_TIMEOUT, TimeUnit.SECONDS);
            return previousCount;
        } catch (Exception e) {
            throw new RuntimeException("qdrant truncateIndex failed: " + e.getMessage(), e);
        }
    }

    protected List<Document> similaritySearchByVector(String indexName, float[] vector, int limit, double minScore) {
        List<Document> results = new ArrayList<>();
        List<Float> boxedVector = new ArrayList<>(vector.length);
        for (float value : vector) {
            boxedVector.add(value);
        }
        Points.SearchPoints searchPoints = Points.SearchPoints.newBuilder()
                .setCollectionName(indexName)
                .addAllVector(boxedVector)
                .setLimit(limit)
                .setWithVectors(Points.WithVectorsSelector.newBuilder().setEnable(true).build())
                .build();
        try {
            List<Points.ScoredPoint> scoredPoints = qdrantClient.searchAsync(searchPoints)
                    .get(WAIT_TIMEOUT, TimeUnit.SECONDS);
            for (Points.ScoredPoint scoredPoint : scoredPoints) {
                if (scoredPoint.getScore() < minScore) {
                    continue;
                }
                String id = String.valueOf(scoredPoint.getId().getNum());
                String content = scoredPoint.getPayloadMap()
                        .getOrDefault("content", JsonWithInt.Value.getDefaultInstance()).getStringValue();
                Map<String, Object> metadata = Collections.mapOf();
                scoredPoint.getPayloadMap().forEach((key, value) -> metadata.put(key, value.getStringValue()));
                Document document = new Document(id, content, metadata);
                document.getMetadata().put("score", (double) scoredPoint.getScore());
                results.add(document);
            }
        } catch (Exception e) {
            log.error("向量搜索失败", e);
        }
        return results;
    }

    @Override
    protected void addEmbeddings(String indexName, List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return;
        }

        List<Points.PointStruct> points = new ArrayList<>(docs.size());
        for (Document doc : docs) {
            float[] embedding = (float[]) doc.getMetadata().get("embedding");
            List<Float> vectorList = new ArrayList<>(embedding.length);
            for (float v : embedding) {
                vectorList.add(v);
            }

            Points.Vector vector = Points.Vector.newBuilder()
                    .addAllData(vectorList)
                    .build();

            Points.Vectors vectors = Points.Vectors.newBuilder()
                    .setVector(vector)
                    .build();

            Map<String, JsonWithInt.Value> payload = new java.util.HashMap<>();
            payload.put("content", JsonWithInt.Value.newBuilder()
                    .setStringValue(doc.getText() != null ? doc.getText() : "").build());

            Points.PointStruct point = Points.PointStruct.newBuilder()
                    .setId(Common.PointId.newBuilder()
                            .setNum(Long.parseLong(doc.getId())).build())
                    .setVectors(vectors)
                    .putAllPayload(payload)
                    .build();

            points.add(point);
        }

        Points.UpsertPoints upsertPoints = Points.UpsertPoints.newBuilder()
                .setCollectionName(indexName)
                .addAllPoints(points)
                .build();

        try {
            qdrantClient.upsertAsync(upsertPoints).get(WAIT_TIMEOUT, TimeUnit.SECONDS);
            log.info("Qdrant 批量 upsert Collection [{}] 完成，文档数={}", indexName, docs.size());
        } catch (Exception e) {
            throw new RuntimeException("Qdrant addEmbeddings failed: " + e.getMessage(), e);
        }
    }

    @Override
    protected void deleteByIds(String indexName, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        List<Common.PointId> pointIds = ids.stream()
                .map(id -> Common.PointId.newBuilder().setNum(Long.parseLong(id)).build())
                .toList();

        try {
            qdrantClient.deleteAsync(indexName, pointIds).get(WAIT_TIMEOUT, TimeUnit.SECONDS);
            log.debug("Qdrant 删除 Collection [{}] 文档，ID数={}", indexName, ids.size());
        } catch (Exception e) {
            throw new RuntimeException("Qdrant deleteByIds failed: " + e.getMessage(), e);
        }
    }

    @Override
    protected List<VectorRecord> getByIds(String indexName, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        List<Common.PointId> pointIds = ids.stream()
                .map(id -> Common.PointId.newBuilder().setNum(Long.parseLong(id)).build())
                .toList();

        try {
            List<Points.RetrievedPoint> retrieved = qdrantClient
                    .retrieveAsync(indexName, pointIds, null)
                    .get(WAIT_TIMEOUT, TimeUnit.SECONDS);

            List<VectorRecord> result = new ArrayList<>(retrieved.size());
            for (Points.RetrievedPoint point : retrieved) {
                String pointId = String.valueOf(point.getId().getNum());
                String content = point.getPayloadMap()
                        .getOrDefault("content", JsonWithInt.Value.getDefaultInstance())
                        .getStringValue();
                result.add(VectorRecord.text(indexName, pointId, content));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Qdrant getByIds failed: " + e.getMessage(), e);
        }
    }

    /**
     * 列出Qdrant中的全部collection。
     *
     * @return collection索引信息
     */
    protected List<IndexInfo> listIndexesImpl() {
        try {
            return qdrantClient.listCollectionsAsync()
                    .get(WAIT_TIMEOUT, TimeUnit.SECONDS)
                    .stream()
                    .map(name -> describeIndexImpl(name))
                    .toList();
        } catch (Exception e) {
            log.warn("Qdrant collection list failed", e);
            throw new RuntimeException("Qdrant listCollections failed: " + e.getMessage(), e);
        }
    }

    /**
     * 查询指定collection的向量维度和距离度量。
     *
     * @param indexName collection名称
     * @return collection描述信息
     */
    protected IndexInfo describeIndexImpl(String indexName) {
        VectorProperties.IndexConfig config = getIndexConfigImpl(indexName);
        return new IndexInfo(indexName, Modality.TEXT, config.getDimension(), config.getMetric(),
                "hnsw", IndexStatus.READY, null, null, null, Map.of());
    }

    /**
     * Qdrant不支持直接修改collection向量配置。
     *
     * @param indexName collection名称
     * @param config 新配置
     * @return 始终返回false
     */
    protected boolean updateIndexConfigImpl(String indexName, VectorProperties.IndexConfig config) {
        log.debug("Qdrant does not support direct collection config updates: name={}", indexName);
        return false;
    }

    /**
     * 查询指定collection的文档数和向量配置。
     *
     * @param indexName collection名称
     * @return collection统计信息
     */
    protected IndexInfo getIndexStatsImpl(String indexName) {
        VectorProperties.IndexConfig config = getIndexConfigImpl(indexName);
        long count = countDocumentsImpl(indexName);
        return new IndexInfo(indexName, Modality.TEXT, config.getDimension(), config.getMetric(),
                "hnsw", IndexStatus.READY, count, null, null, Map.of());
    }


    public List<VectorSearchResult> searchByVector(String indexName, float[] queryVector, int limit) {
        List<VectorSearchResult> results = new ArrayList<>();

        List<Float> boxedVector = new ArrayList<>(queryVector.length);
        for (float v : queryVector) {
            boxedVector.add(v);
        }

        Points.SearchPoints searchPoints = Points.SearchPoints.newBuilder()
                .setCollectionName(indexName)
                .addAllVector(boxedVector)
                .setLimit(limit)
                .setWithVectors(Points.WithVectorsSelector.newBuilder().setEnable(true).build())
                .build();

        // 异步执行向量搜索
        ListenableFuture<List<Points.ScoredPoint>> resp = qdrantClient.searchAsync(searchPoints);

        try {
            // 等待搜索结果，设置超时
            List<Points.ScoredPoint> scoredPoints = resp.get(WAIT_TIMEOUT, TimeUnit.SECONDS);
            for (Points.ScoredPoint scoredPoint : scoredPoints) {
                // 提取文档ID，从Qdrant的PointId中获取数值并转为字符串
                String id = String.valueOf(scoredPoint.getId().getNum());

                // 提取文档内容，从payload中获取"content"字段
                // 使用空字符串作为默认值，避免null值
                String content = "";
                if (scoredPoint.getPayloadMap().containsKey("content")) {
                    content = scoredPoint.getPayloadMap().get("content").getStringValue();
                }

                // 获取相似度分数，Qdrant返回的score已经是double类型
                float score = scoredPoint.getScore();

                // 提取原始向量数据，可能为空（某些查询不返回向量）
                float[] vector = null;
                if (scoredPoint.hasVectors() && scoredPoint.getVectors().getVector().getDataCount() > 0) {
                    // 将List<Float>转换为float[]，与查询向量的处理方式一致
                    List<Float> dataList = scoredPoint.getVectors().getVector().getDataList();
                    vector = new float[dataList.size()];
                    for (int i = 0; i < dataList.size(); i++) {
                        vector[i] = dataList.get(i);
                    }
                }

                // 构建搜索结果对象，包含ID、内容、分数和向量
                results.add(VectorSearchResult.of(id, content, (double) score, vector));
            }
        } catch (Exception e) {
            // 搜索失败时记录错误并返回空列表
            // 调用方需要处理空结果列表的情况
            log.error("向量搜索失败", e);
        }

        return results;
    }

    /**
     * 触发 collection 优化（force optimizer + snapshot）。
     *
     * <p>Qdrant 没有专门的 "optimize" API。创建快照（{@code createSnapshotAsync}）
     * 会触发 segments optimizer 与索引合并，作为可观察的优化副作用。
     * 失败时返回 {@code false} 而非抛异常，便于上层健康检查容忍降级。</p>
     *
     * @param indexName collection 名称
     * @return 优化是否成功触发
     */
    @Override
    protected boolean optimizeImpl(String indexName) {
        try {
            qdrantClient.createSnapshotAsync(indexName).get(WAIT_TIMEOUT, TimeUnit.SECONDS);
            log.info("Qdrant optimize (snapshot 触发) 完成: name={}", indexName);
            return true;
        } catch (Exception e) {
            log.warn("Qdrant optimize failed for [{}]: {}", indexName, e.getMessage());
            return false;
        }
    }

    /**
     * Qdrant 不支持独立 alias 概念 —— collection 本身就是逻辑名。
     *
     * @throws UnsupportedOperationException 始终抛出
     */
    @Override
    protected boolean createAliasImpl(String indexName, String alias) {
        return throwUnsupportedOps("createAlias", indexName, "qdrant");
    }

    /**
     * Qdrant 无 alias —— 重建 collection 即可实现零停机切换，
     * 参考 {@code AbstractVectorService.cloneIndex}。
     *
     * @throws UnsupportedOperationException 始终抛出
     */
    @Override
    protected boolean switchAliasImpl(String oldIndexName, String newIndexName, String alias) {
        return throwUnsupportedOps("switchAlias", newIndexName, "qdrant");
    }

    /**
     * Qdrant backup 需通过 {@code qdrant-backup} 工具（out-of-process），
     * VectorService 层不直接支持；可使用 {@code createSnapshotAsync} 在 Qdrant 内部创建快照。
     *
     * @throws UnsupportedOperationException 始终抛出
     */
    @Override
    protected boolean backupImpl(String indexName, String targetPath) {
        return throwUnsupportedOps("backup", indexName, "qdrant");
    }

    /**
     * Qdrant restore 需通过 {@code qdrant-backup} 工具（out-of-process），
     * VectorService 层不直接支持。
     *
     * @throws UnsupportedOperationException 始终抛出
     */
    @Override
    protected boolean restoreImpl(String sourcePath, String indexName) {
        return throwUnsupportedOps("restore", indexName, "qdrant");
    }

}