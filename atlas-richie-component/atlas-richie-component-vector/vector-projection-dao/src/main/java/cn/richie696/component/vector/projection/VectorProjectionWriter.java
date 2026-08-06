package cn.richie696.component.vector.projection;

import cn.richie696.component.vector.bulk.BulkOperationEvent;
import cn.richie696.component.vector.model.VectorRecord;
import reactor.core.publisher.Flux;

/**
 * 将指定投影版本的向量记录写入向量库，并同步维护 vectorId manifest。
 *
 * <p>本接口是 vector 投影插件的写入 SPI：把待写入的 {@link VectorRecord} 流投影到目标
 * 向量库，同时把每条记录对应的 provider vectorId 记录到 manifest（同一关系库事务），
 * 供后续 cleanup 阶段精确删除使用。
 *
 * <p>它解决"如何让写入过程既能观察进度、又能保证清理时可定位到具体向量"的问题——写入流
 * 返回的 {@link BulkOperationEvent} 既驱动 lifecycle 状态推进（WRITING → READY），
 * 又是下游 UI / 监控的进度来源；manifest 让 cleanup 服务即便在 provider 不支持
 * {@code deleteByDocumentId} 或 metadata filter 时也能按 vectorId 精确回收。
 *
 * <p>调用关系：业务重建流程在 {@link VectorProjectionLifecycleService#beginRebuild} 拿到
 * {@code versionId} 后调用本接口；订阅本接口返回的 {@link Flux} 必须正常完成才能保证
 * manifest 完整。实现依赖 {@link VectorProjectionVersion} 的版本信息（reference /
 * specification）写入 metadata，依赖底层 {@code VectorService.addBatch} 完成实际向量
 * 写入，依赖同一事务将 vectorId 写入 manifest。写入成功后才允许调用
 * {@link VectorProjectionLifecycleService#activate}。
 *
 * <p>失败语义：当写入流异常终止时，调用方应保留已部分写入的 vectorId 列表（通过重订阅
 * {@link BulkOperationEvent} 收集），并在判断不可恢复时调用
 * {@link VectorProjectionLifecycleService#markFailed}；本接口不承诺幂等，业务侧需配合
 * {@code VectorRecordDeleteOperations.deleteByIds} 的幂等删除做安全重试。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public interface VectorProjectionWriter {
    /**
     * 把记录流写入到指定投影版本对应的目标向量库。
     *
     * <p>调用方应订阅返回的 {@link Flux} 直到 {@code complete()} 事件，再据此推进
     * {@link VectorProjectionLifecycleService#activate}；中途取消订阅视作放弃本次
     * 重建，应配合 {@code markFailed} 处理。返回的事件流包含每个批次（嵌入、写入、
     * 完成、失败）的进度，可用于驱动上层 UI 与对账。
     *
     * @param versionId 目标投影版本标识；必须由
     *                  {@link VectorProjectionLifecycleService#beginRebuild} 生成且处于非终态。
     * @param records   待写入的向量记录流；每条记录的 {@link cn.richie696.component.vector.model.VectorContent}
     *                  必须与目标 {@code embeddingSpaceId} 兼容。
     * @return 写入过程中的批量操作事件流；调用方必须订阅该 Flux 才能保证 manifest 与
     * 向量库写入同步完成。
     * @throws IllegalArgumentException 当 versionId 为空或 records 为 {@code null} 时。
     * @throws IllegalStateException    当 versionId 不存在，或版本已处于终态（CLEANED / FAILED）。
     */
    Flux<BulkOperationEvent> write(String versionId, Flux<VectorRecord> records);
}
