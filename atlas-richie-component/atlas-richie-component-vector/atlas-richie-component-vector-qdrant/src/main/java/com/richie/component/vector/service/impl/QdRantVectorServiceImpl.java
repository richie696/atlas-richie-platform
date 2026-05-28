package com.richie.component.vector.service.impl;

import com.richie.context.utils.data.Collections;
import com.richie.component.vector.config.VectorProperties;
import com.richie.component.vector.model.VectorDocument;
import com.richie.component.vector.model.VectorSearchResult;
import com.richie.component.vector.service.VectorService;
import com.google.common.util.concurrent.ListenableFuture;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points;
import lombok.extern.slf4j.Slf4j;
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
 * @author Rydeen Platform Team
 * @version 1.0.0-SNAPSHOT
 * @since 1.0.0
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "platform.component.vector", name = "provider", havingValue = "qdrant")
public class QdRantVectorServiceImpl extends VectorServiceImpl implements VectorService {

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
     * @param vectorStore Spring AI的VectorStore实现，用于文档的添加和删除
     * @param embeddingModel 嵌入模型，用于将文本转换为向量
     * @param qdrantClient Qdrant客户端，提供与Qdrant服务的低级API交互
     */
    @Autowired
    public QdRantVectorServiceImpl(VectorStore vectorStore,
                                   @Qualifier("aiEmbeddingModel") EmbeddingModel embeddingModel,
                                   QdrantClient qdrantClient) {
        super(vectorStore, embeddingModel);
        this.qdrantClient = qdrantClient;
    }

    /**
     * 创建向量索引.
     *
     * <p><b>不支持的操作：</b>Qdrant作为托管型向量数据库，采用schema-less设计，
     * 不需要在使用前显式创建索引结构。索引会在添加文档时自动创建。</p>
     *
     * @param indexName 索引名称（Qdrant中称为collection）
     * @param config 索引配置（Qdrant不支持此参数）
     * @throws UnsupportedOperationException 始终抛出，因为Qdrant不需要显式创建索引
     */
    @Override
    public void createIndex(String indexName, VectorProperties.IndexConfig config) {
        throw new UnsupportedOperationException("qdrant不支持索引功能");
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
    public void deleteIndex(String indexName) {
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
    public boolean indexExists(String indexName) {
        throw new UnsupportedOperationException("qdrant不支持索引功能");
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
    public VectorProperties.IndexConfig getIndexConfig(String indexName) {
        throw new UnsupportedOperationException("qdrant不支持索引功能");
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
    public long countDocuments(String indexName) {
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
     *   <li>将剩余文档转换为VectorDocument并添加到结果列表</li>
     *   <li>当结果数量达到limit时停止查询</li>
     *   <li>当返回空列表或没有下一页offset时说明数据已遍历完毕</li>
     * </ol>
     *
     * @param indexName collection名称
     * @param offset 跳过的文档数量，用于分页
     * @param limit 返回的最大文档数量
     * @return 文档列表，不足limit时可能少于指定数量
     */
    @Override
    protected List<VectorDocument> listDocumentsHandler(String indexName, int offset, int limit) {
        List<VectorDocument> docs = new ArrayList<>();
        Long lastId = null;
        int skipped = 0;

        // 循环分页获取文档，直到达到limit或数据遍历完毕
        while (docs.size() < limit) {
            // 构建scroll请求，启用向量返回以便完整还原VectorDocument
            // enable=true表示返回所有向量数据，使文档可被重新用于相似度计算
            Points.WithVectorsSelector withVectorsSelector = Points.WithVectorsSelector.newBuilder()
                    .setEnable(true) // true 表示返回所有向量
                    .build();

            // 构建scroll points请求，设置分页参数
            // offset使用上一个文档的ID实现游标分页，避免offset大时的性能问题
            var scoredPoints = Points.ScrollPoints.newBuilder()
                    .setCollectionName(indexName)
                    .setLimit(limit)
                    // 首次查询offset为null，后续使用lastId作为游标
                    .setOffset(lastId != null ? Points.PointId.newBuilder().setNum(lastId).build() : null)
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

                // 将Qdrant的Point转换为VectorDocument
                VectorDocument doc = new VectorDocument();
                doc.setId(String.valueOf(point.getId().getNum()));

                // 将Qdrant的Float列表转换为float数组
                // Qdrant存储float列表，需要转换为我们需要的float[]类型
                List<Float> dataList = point.getVectors().getVector().getDataList();
                float[] vectorArray = new float[dataList.size()];
                for (int i = 0; i < dataList.size(); i++) {
                    vectorArray[i] = dataList.get(i);
                }
                doc.setVector(vectorArray);

                // 提取payload中的元数据
                // Qdrant的payload是Map<String, Value>，需要转换为Map<String, Object>
                // 使用getStringValue提取字符串类型的值，其他类型暂不处理
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

    /**
     * 通过向量进行相似度搜索.
     *
     * <p>将查询向量发送到Qdrant，使用向量相似度计算找到最相似的文档。
     * 返回结果包含文档ID、内容、相似度分数和原始向量。</p>
     *
     * <p>搜索流程：</p>
     * <ol>
     *   <li>将float数组转换为List&lt;Float&gt;以适配Qdrant API</li>
     *   <li>构建SearchPoints请求，指定collection名称和查询向量</li>
     *   <li>异步执行搜索，设置返回向量以便后续处理</li>
     *   <li>遍历结果，将Qdrant的ScoredPoint转换为VectorSearchResult</li>
     * </ol>
     *
     * @param queryVector 查询向量，用于计算相似度
     * @param limit 返回的最大结果数量
     * @return 搜索结果列表，按相似度分数降序排列
     */
    @Override
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

}