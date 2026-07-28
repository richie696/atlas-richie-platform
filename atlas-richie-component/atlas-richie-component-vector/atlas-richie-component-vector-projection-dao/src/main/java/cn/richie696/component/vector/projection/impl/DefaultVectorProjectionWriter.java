package cn.richie696.component.vector.projection.impl;

import cn.richie696.component.vector.bulk.BulkOperationEvent;
import cn.richie696.component.vector.model.VectorRecord;
import cn.richie696.component.vector.projection.VectorProjectionVersion;
import cn.richie696.component.vector.projection.VectorProjectionWriter;
import cn.richie696.component.vector.service.VectorBulkOperations;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

import java.util.HashMap;

/**
 * {@link VectorProjectionWriter} 的默认实现 —— 把 vector 组件的 bulk 写入事件投影成
 * 投影版本状态切换和 vectorId manifest 落库。
 * <p>
 * <strong>本类是什么</strong>：位于 {@code impl} 包内，作为 bulk 写入侧在状态机上的"镜像 sink"。
 * 业务调用方调用 {@link #write(String, Flux)} 后，本类负责：
 * <ul>
 *   <li>把每条 {@link VectorRecord} 的 metadata 补齐成"租户 / 知识库 / 投影 / 版本 / 源版本 / 嵌入空间"6 个固定字段；</li>
 *   <li>把 bulk 写入产生的 {@link BulkOperationEvent} 回调到状态机（成功→{@code recordSucceeded}，失败→{@code recordFailure}）；</li>
 *   <li>流正常完成时把版本标记为 {@code READY}（无失败时）或 {@code FAILED}（有失败时已经被 {@code markFailure} 标记）。</li>
 * </ul>
 * <p>
 * <strong>为什么存在</strong>：vector 组件自身的 {@link VectorBulkOperations} 不知道投影的存在；
 * 业务方如果在文档层调用 upsertAll，无法保证写入与版本状态机同步。该实现把 bulk 写入与投影历史
 * 紧紧绑定，让"同一 sourceVersion 的所有向量"作为同一 ACTIVE 切换的原子粒度。
 * <p>
 * <strong>在投影生命周期中的位置</strong>：承接 {@code beginRebuild → WRITING → READY} 这一段，
 * 是生命周期最热的一段。流每完成一次，状态机就推进一次；{@code activate} 由调用方在
 * 整个 Flux 正常完成后再触发。
 * <p>
 * <strong>调用关系</strong>：由 {@link cn.richie696.component.vector.projection.config.VectorProjectionDaoAutoConfiguration}
 * 自动装配（{@code vectorProjectionWriter} Bean），运行时被业务侧的"重建 / 增量同步"服务调用。
 * 依赖：
 * <ul>
 *   <li>{@link DefaultVectorProjectionService} —— 状态机本体，本类作为其写入侧 sink；</li>
 *   <li>{@link VectorBulkOperations} —— vector 组件本身的 bulk 写入抽象（不感知具体 provider）。</li>
 * </ul>
 * <p>
 * <strong>关键不变量</strong>：
 * <ul>
 *   <li>本类始终是状态机的下游 sink，不修改状态机本身的 @Transactional 边界；</li>
 *   <li>本类在 {@link Flux#defer} 内执行状态查询与状态切换，避免在订阅前触发事务；</li>
 *   <li>本类不写 projection / version 表，只通过 service 间接驱动；manifest 写入由
 *       {@link DefaultVectorProjectionService#recordSucceeded} 完成。</li>
 * </ul>
 * <p>
 * 构造器由 Lombok {@link RequiredArgsConstructor} 生成，依赖注入关系（顺序 = 字段声明顺序）：
 * {@code (DefaultVectorProjectionService, VectorBulkOperations)}，每个字段的 Javadoc 即构造器
 * 参数的契约说明。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
@RequiredArgsConstructor
public class DefaultVectorProjectionWriter implements VectorProjectionWriter {

    /**
     * 状态机 Bean —— 由 {@link cn.richie696.component.vector.projection.config.VectorProjectionDaoAutoConfiguration}
     * 中 {@code vectorProjectionService} 装配产生；本类在每条 bulk 事件回调时都会调用其状态切换方法。
     * 构造器参数 1。
     */
    private final DefaultVectorProjectionService lifecycle;

    /**
     * vector 组件的 bulk 写入抽象 —— 不感知具体 provider（Redis / Milvus / Qdrant 等），
     * 仅负责把 {@link VectorRecord} 流按批次写入向量库并发出 {@link BulkOperationEvent} 事件流。
     * 构造器参数 2。
     */
    private final VectorBulkOperations vectorBulkOperations;

    /**
     * 把用户给定的 vector 记录流写入当前 {@code versionId} 对应的 vector 索引，并同步推进状态机。
     * <p>
     * 前置条件：
     * <ul>
     *   <li>{@code versionId} 必须是已 {@link DefaultVectorProjectionService#beginRebuild 开启} 的版本；</li>
     *   <li>该版本当前状态应为 {@code PREPARING}（{@link #write} 内会先切到 {@code WRITING}）；</li>
     *   <li>调用方应在 Flux 正常完成后才 {@link DefaultVectorProjectionService#activate 激活}。</li>
     * </ul>
     * <p>
     * 副作用：
     * <ul>
     *   <li>对每条 record 调用 {@link #decorate} 补齐 6 个 metadata 字段 + {@code documentId}；</li>
     *   <li>每个 {@link BulkOperationEvent.ItemSucceeded} 触发 {@code recordSucceeded}，向
     *       {@code rag_vector_projection_record} 表插一条 vectorId manifest；</li>
     *   <li>每个 {@link BulkOperationEvent.ItemFailed} 触发 {@code recordFailure}，累加
     *       {@code failed_records} 计数；</li>
     *   <li>流完成时调用 {@code markReadyIfNoFailure}；流异常时调用 {@code failFromWriter}。</li>
     * </ul>
     * <p>
     * 失败语义：bulk 写入的 provider 异常会通过 {@code doOnError} 触发 {@code failFromWriter} 把
     * 版本置为 {@code FAILED}；subscription 之前的状态查询 / 切到 {@code WRITING} 是同步事务，
     * 后续的 manifest 写入各自独立事务。{@link Flux#defer} 保证 subscribe 时才真正查版本。
     *
     * @param versionId 投影版本 ID（{@link DefaultVectorProjectionService#beginRebuild} 返回的快照中的 {@code versionId}）
     * @param records   待写入的向量记录流；每条 record 的 {@code indexName} 若缺失会使用版本 spec 中的 indexName，否则必须与之匹配
     * @return bulk 写入事件流（{@link BulkOperationEvent}），订阅方可继续观察进度或与状态机交互
     * @throws IllegalArgumentException 当 {@code versionId} 不存在或 record 的 indexName 与版本不匹配时
     */
    @Override
    public Flux<BulkOperationEvent> write(String versionId, Flux<VectorRecord> records) {
        return Flux.defer(() -> {
            VectorProjectionVersion version = lifecycle.findVersion(versionId)
                    .orElseThrow(() -> new IllegalArgumentException("projection version does not exist: " + versionId));
            lifecycle.markWriting(versionId);
            Flux<VectorRecord> decorated = records.map(record -> decorate(record, version));
            return vectorBulkOperations.upsertAll(version.specification().indexName(), decorated)
                    .doOnNext(event -> observe(versionId, event))
                    .doOnComplete(() -> lifecycle.markReadyIfNoFailure(versionId))
                    .doOnError(error -> lifecycle.failFromWriter(versionId, error));
        });
    }

    /**
     * 把单条 bulk 事件回调到状态机。
     * <p>
     * 类型分支：
     * <ul>
     *   <li>{@link BulkOperationEvent.ItemSucceeded} → {@code lifecycle.recordSucceeded}（插 manifest + 累加 written_records）；</li>
     *   <li>{@link BulkOperationEvent.ItemFailed} → {@code lifecycle.recordFailure}（累加 failed_records + 写失败原因）。</li>
     * </ul>
     * 本方法不修改状态，只作为单条事件的副作用入口。
     *
     * @param versionId 投影版本 ID
     * @param event     bulk 写入产生的单条事件
     */
    private void observe(String versionId, BulkOperationEvent event) {
        if (event instanceof BulkOperationEvent.ItemSucceeded success) {
            lifecycle.recordSucceeded(versionId, success.vectorId());
        } else if (event instanceof BulkOperationEvent.ItemFailed failure) {
            lifecycle.recordFailure(versionId, failure.errorCode() + ": " + failure.message());
        }
    }

    /**
     * 把版本信息投影到每条 {@link VectorRecord} 的 metadata 上 —— 这是"可重建投影"语义的核心：
     * 检索时业务方可以通过 metadata 同时拿到 tenant / knowledgeBase / projection / version / sourceVersion / embeddingSpace，
     * 无需回查关系库。
     * <p>
     * 校验：
     * <ul>
     *   <li>record 为 {@code null} 时抛 {@link IllegalArgumentException}；</li>
     *   <li>record 未设置 {@code indexName} 时使用版本 spec 的 indexName；</li>
     *   <li>record 已设置 {@code indexName} 时必须与版本 spec 一致，否则抛 {@link IllegalArgumentException}（防止误写）。</li>
     * </ul>
     * 副作用：每条 record 都会被改写 {@code documentId}（取自版本的 {@code documentRef}）与 metadata。
     *
     * @param record  业务侧传入的待写入 record（空 metadata 也会被规整为 HashMap）
     * @param version 投影版本快照，提供 tenant / kb / projection / version / source / embedding 6 个投影字段
     * @return 装饰后的 record（同一对象，原地修改）
     * @throws IllegalArgumentException record 为空或 record 的 indexName 与版本不一致
     */
    private VectorRecord decorate(VectorRecord record, VectorProjectionVersion version) {
        if (record == null) {
            throw new IllegalArgumentException("VectorRecord must not be null");
        }
        if (record.getIndexName() == null || record.getIndexName().isBlank()) {
            record.setIndexName(version.specification().indexName());
        }
        if (!version.specification().indexName().equals(record.getIndexName())) {
            throw new IllegalArgumentException("record indexName does not match projection version");
        }
        record.setDocumentId(version.reference().documentRef());
        HashMap<String, Object> metadata = new HashMap<>(record.getMetadata() == null ? java.util.Map.of() : record.getMetadata());
        metadata.put("tenantId", version.reference().tenantId());
        metadata.put("knowledgeBaseId", version.reference().knowledgeBaseId());
        metadata.put("projectionId", version.projectionId());
        metadata.put("projectionVersionId", version.versionId());
        metadata.put("sourceVersion", version.specification().sourceVersion());
        metadata.put("embeddingSpaceId", version.specification().embeddingSpaceId());
        metadata.put("status", "ACTIVE");
        record.setMetadata(metadata);
        return record;
    }
}
