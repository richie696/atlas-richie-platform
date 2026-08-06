package cn.richie696.component.vector.projection.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * 向量投影 DAO 插件的配置项（{@code @ConfigurationProperties}）。
 * <p>
 * 绑定的 YAML 前缀是 {@code platform.component.vector.projection}，由 Spring Boot 在
 * 上下文初始化阶段自动注入到 {@link VectorProjectionDaoAutoConfiguration}，并进一步装配
 * {@link cn.richie696.component.vector.projection.impl.DefaultVectorProjectionService} 等核心 Bean。
 * 业务侧只通过本类调参，无需直接持有任何 Mapper 或 TransactionTemplate。
 * <p>
 * 本类只承载"何时启用 / 清理策略 / 批次大小"三组参数；向量库连接、Embedding 模型等更底层的
 * 配置仍由 {@code platform.component.vector.*} 其它子节点负责，二者职责互不重叠。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
@Data
@ConfigurationProperties(prefix = "platform.component.vector.projection")
public class VectorProjectionDaoProperties {

    /**
     * 是否启用向量投影 DAO 插件。
     * <p>
     * 完整 YAML 路径：{@code platform.component.vector.projection.enabled}。
     * <p>
     * 默认值：{@code false}（必须显式开启）。
     * <p>
     * 典型值：{@code true}（商用 RAG 场景需要版本保留 / 失败重试 / 精确清理）。
     * <p>
     * 业务含义：默认关闭是为了避免引入本模块就强制业务创建
     * {@code rag_vector_projection}、{@code rag_vector_projection_version}、
     * {@code rag_vector_projection_record}、{@code rag_vector_projection_outbox} 等关系库表。
     * 启用前请确认已执行 {@code schema/vector-projection-schema.sql} 中的 DDL（或等价的
     * Flyway/Liquibase migration）。该字段同时充当
     * {@link VectorProjectionDaoAutoConfiguration} 上的 {@code @ConditionalOnProperty} 开关。
     */
    private boolean enabled;

    /**
     * 新版本 {@code ACTIVE} 之后，旧版本进入 {@code RETIRING} 的默认延迟清理时长。
     * <p>
     * 完整 YAML 路径：{@code platform.component.vector.projection.cleanup-delay}。
     * <p>
     * 默认值：{@code 24h}（{@link Duration#ofHours(int) 24 小时}）。
     * <p>
     * 典型值：{@code 12h}、{@code 48h}、{@code 7d}。
     * <p>
     * 业务含义：{@link cn.richie696.component.vector.projection.VectorProjectionLifecycleService#activate(String, Duration)}
     * 允许调用方在运行时覆盖该值；本字段作为兜底，避免业务忘传 cleanupDelay 时旧版本永远不被清理。
     * 设得过短会导致仍在被读链路使用的旧版本被提前清理；设得过长会让 vector 库中残留更多向量。
     */
    private Duration cleanupDelay = Duration.ofHours(24);

    /**
     * 清理任务单次从关系库读取并下发的 vectorId 最大批次。
     * <p>
     * 完整 YAML 路径：{@code platform.component.vector.projection.delete-batch-size}。
     * <p>
     * 默认值：{@code 200}。
     * <p>
     * 典型值：Redis 向量库建议不超过 {@code 300}；Milvus / Qdrant 等可放宽至 {@code 500}；
     * 同步阻塞式 provider 建议调小到 {@code 50}–{@code 100}。
     * <p>
     * 业务含义：清理流程按该批次循环读取 manifest 中的 vectorId，调用
     * {@link cn.richie696.component.vector.service.VectorRecordDeleteOperations#deleteByIds(String, java.util.List)}
     * 逐批删除向量库，再写入关系库删除 manifest。该值同时受 vector provider 的单次请求上限约束，
     * 超过 provider 限制可能导致 delete 部分失败并需要重试。
     */
    private int deleteBatchSize = 200;
}
