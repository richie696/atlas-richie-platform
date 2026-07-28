package cn.richie696.component.vector.service;

import cn.richie696.component.vector.bulk.BulkOperationEvent;
import cn.richie696.component.vector.model.VectorRecord;
import reactor.core.publisher.Flux;

/**
 * 大规模向量数据操作能力。
 *
 * <p>它是 {@link VectorService} 的四个必选子接口之一，专门处理"上万/百万级向量数据
 * 入库与删除"的反应式事件流场景。核心职责是把 {@link VectorRecord} 的批处理从
 * "阻塞调用 + 自己分块" 抽到反应式管道上，使业务层能在 UI / 异步任务中订阅进度、
 * 取消、错误恢复。</p>
 *
 * <p>返回的 {@link Flux} 为冷流：订阅开始执行，取消订阅即请求取消。该接口只负责一次运行中的
 * 实时事件；跨进程任务持久化、断点续跑和消息投递由知识库应用层负责。</p>
 *
 * <p>关键设计取舍：
 * <ul>
 *   <li><b>冷流而非热流</b>：不缓存事件历史，每次 {@code subscribe()} 都是一次新执行，
 *       避免"再次订阅拿到陈旧事件"的歧义</li>
 *   <li><b>事件而非回调</b>：业务层通过 {@code Flux<BulkOperationEvent>} 订阅，
 *       自然获得 {@code map/filter/retry/doOnError} 等响应式算子</li>
 *   <li><b>取消语义明确</b>：下游 {@code subscription.cancel()} 会通过
 *       {@link reactor.core.publisher.Flux#doOnCancel} 等机制让 provider 停止后续写入，
 *       但已经发出的写入由 provider 自行保证原子性</li>
 * </ul>
 *
 * <p>调用关系：
 * <ul>
 *   <li>由 {@link VectorService} 继承暴露给业务层</li>
 *   <li>由 {@code AbstractVectorService} 委托给 {@code BulkIngestionPipeline} 编排</li>
 *   <li>由业务层（批量入库任务、迁移脚本、UI 进度展示）订阅消费</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public interface VectorBulkOperations {

    /**
     * 批量 upsert：把 {@link VectorRecord} 流写入索引。
     *
     * <p>事件流典型顺序：
     * {@code Started → (ItemStarted → ItemSucceeded | ItemFailed)* → Completed(summary)}。
     * 单条失败默认不影响其它条目（{@code failFast=false}），由 provider 决定是否终止。</p>
     *
     * @param indexName 索引名称，非空
     * @param records   待 upsert 的向量记录冷流；订阅时开始执行；非空（但流本身可为空）
     * @return {@link BulkOperationEvent} 冷流；订阅时启动流水线，取消时尝试停止
     * @throws IllegalArgumentException 索引名为空时通过 {@code onError} 发出
     */
    Flux<BulkOperationEvent> upsertAll(String indexName, Flux<VectorRecord> records);

    /**
     * 批量删除：按 vectorId 流删除索引内记录。
     *
     * <p>每条 ID 独立的 {@code ItemStarted → ItemSucceeded/ItemFailed}，整体并发度由
     * {@code VectorProperties.Bulk.writeConcurrency} 控制。空 ID 在流内视为非法入参，
     * 由 provider 报告为 {@code ItemFailed} 而非抛顶层异常。</p>
     *
     * @param indexName 索引名称，非空
     * @param vectorIds 待删除的 vectorId 冷流；非空
     * @return {@link BulkOperationEvent} 冷流
     */
    Flux<BulkOperationEvent> deleteAll(String indexName, Flux<String> vectorIds);
}
