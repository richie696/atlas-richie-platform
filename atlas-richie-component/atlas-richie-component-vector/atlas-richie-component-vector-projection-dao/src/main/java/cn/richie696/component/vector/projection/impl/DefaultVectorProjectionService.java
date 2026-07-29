package cn.richie696.component.vector.projection.impl;

import cn.richie696.component.vector.knowledge.ActiveProjectionVersionResolver;
import cn.richie696.component.vector.projection.*;
import cn.richie696.component.vector.projection.config.VectorProjectionDaoProperties;
import cn.richie696.component.vector.projection.persistence.*;
import cn.richie696.component.vector.service.VectorRecordDeleteOperations;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;

/**
 * 基于关系库的向量投影状态机及延迟清理默认实现。
 * <p>
 * <strong>本类是什么</strong>：模块最核心的编排器，同时实现三个接口：
 * <ul>
 *   <li>{@link VectorProjectionLifecycleService} —— 投影版本状态机的写入 / 激活 / 失败转移入口；</li>
 *   <li>{@link VectorProjectionCleanupService} —— 延迟清理任务的调度入口；</li>
 *   <li>{@link ActiveProjectionVersionResolver} —— 按 tenant / knowledgeBase 解析当前 ACTIVE 版本集合。</li>
 * </ul>
 * 所有状态变更与 manifest 写入均落在同一关系库事务内；向量库的写入与删除不在本类事务范围内。
 * <p>
 * <strong>为什么存在</strong>：商用 RAG 场景下，向量库需要"可重建投影"——同一文档在同一向量库内
 * 必须按版本严格隔离，激活后才能被检索，旧版本到点后由 manifest 批量清理。vector 组件本身
 * 不感知版本，因此本类承担调度 + 状态机 + 关系库持久化 + outbox 协同的全部职责。
 * <p>
 * <strong>在投影生命周期中的位置</strong>：
 * <pre>
 *      beginRebuild          activate                cleanupDueProjections
 *  PREPARING ──────→ WRITING ──────→ READY ──────→ ACTIVE ──────→ RETIRING ──────→ CLEANED
 *        └─────────→ FAILED   (markFailed / failFromWriter)
 * </pre>
 * 调用方典型路径：{@code beginRebuild} →（订阅 {@link DefaultVectorProjectionWriter#write}）→
 * {@code activate} →（外部调度器周期触发）{@code cleanupDueProjections}。
 * <p>
 * <strong>调用关系</strong>：由 {@link cn.richie696.component.vector.projection.config.VectorProjectionDaoAutoConfiguration}
 * 自动装配（{@code vectorProjectionService} Bean）。运行时被业务侧"重建 / 增量同步"服务调用，
 * 同时被 {@link DefaultVectorProjectionWriter} 回调以推进状态机。依赖（每个字段的 Javadoc 即构造器参数的契约）：
 * <ul>
 *   <li>4 个关系库 Mapper —— 投影、版本、manifest、outbox；</li>
 *   <li>{@link VectorRecordDeleteOperations} —— 清理阶段按 manifest 批量删除向量；</li>
 *   <li>{@link VectorProjectionDaoProperties} —— cleanupDelay / deleteBatchSize；</li>
 *   <li>{@link TransactionTemplate} —— 清理批次用独立事务（避免与状态机事务传播冲突）。</li>
 * </ul>
 * <p>
 * <strong>关键不变量 / 失败语义</strong>：
 * <ul>
 *   <li>{@code beginRebuild / activate / markFailed} 由 {@link Transactional} 标注，失败时整段回滚；
 *       状态字段、版本 active 切换、outbox 入队都在同一事务内一致提交；</li>
 *   <li>{@code cleanupVersion} 中批量删除向量与删除 manifest 各自独立事务 —— 中途失败时，
 *       已成功的批次已完成，已删除的向量不会重复清理；剩余批次由下次调度重新拉取；</li>
 *   <li>本类不会自己启动任何调度线程 —— {@link #cleanupDueProjections} 必须由调用方通过
 *       Quartz / XXL-Job 等外部调度器周期触发；</li>
 *   <li>本类不感知业务文档表，只接管投影相关的 4 张表。</li>
 * </ul>
 * <p>
 * 构造器由 Lombok {@link RequiredArgsConstructor} 生成，依赖注入关系（顺序 = 字段声明顺序）：
 * {@code (VectorProjectionMapper, VectorProjectionVersionMapper, VectorProjectionRecordMapper,
 * VectorProjectionOutboxMapper, VectorRecordDeleteOperations, VectorProjectionDaoProperties,
 * TransactionTemplate)}，每个字段的 Javadoc 即构造器参数的契约说明。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultVectorProjectionService implements VectorProjectionLifecycleService, VectorProjectionCleanupService,
        ActiveProjectionVersionResolver {

    /**
     * outbox 事件类型常量 —— 标识"清理任务"事件。清理任务入队时写入该类型，清理完成后
     * 同类型事件被标记为 {@link #OUTBOX_PROCESSED}。
     */
    static final String CLEANUP_EVENT = "VECTOR_PROJECTION_CLEANUP";

    /**
     * outbox 状态常量 —— 待处理。清理任务在 {@link #activate} 中入队，状态取此值。
     */
    static final String OUTBOX_PENDING = "PENDING";

    /**
     * outbox 状态常量 —— 已处理。清理完成时由 {@link #finishCleanup} 把状态置为该值。
     */
    static final String OUTBOX_PROCESSED = "PROCESSED";

    /**
     * 投影主表 Mapper —— 业务上按 {@code tenantId + knowledgeBaseId + documentRef} 唯一定位一条
     * 投影记录。构造器参数 1。
     */
    private final VectorProjectionMapper projectionMapper;

    /**
     * 投影版本表 Mapper —— 状态机主表，每条记录代表一个 projection version。构造器参数 2。
     */
    private final VectorProjectionVersionMapper versionMapper;

    /**
     * vectorId manifest 表 Mapper —— 记录每次写入产生的 vectorId，归清理阶段消费。
     * 构造器参数 3。
     */
    private final VectorProjectionRecordMapper recordMapper;

    /**
     * outbox 事件表 Mapper —— 承载清理任务的事件流。构造器参数 4。
     */
    private final VectorProjectionOutboxMapper outboxMapper;

    /**
     * vector 库删除操作抽象 —— 清理阶段按 vectorId 批量删除向量。构造器参数 5。
     */
    private final VectorRecordDeleteOperations deleteOperations;

    /**
     * 插件配置 —— 提供 {@code cleanupDelay} / {@code deleteBatchSize} 两个核心参数。
     * 构造器参数 6。
     */
    private final VectorProjectionDaoProperties properties;

    /**
     * 独立事务模板 —— 清理阶段单次批次用独立事务提交，避免与状态机事务传播冲突。
     * 构造器参数 7。
     */
    private final TransactionTemplate transactionTemplate;

    /**
     * 开启一次重建，原子地创建或复用 projection 主表记录并新建一条 {@code PREPARING} 版本。
     * <p>
     * 前置条件：
     * <ul>
     *   <li>{@code reference} 与 {@code specification} 都必须非空；</li>
     *   <li>{@code reference.tenantId()} 等 3 个字段在业务上必须共同定位一个幂等的 {@code projection} 记录。</li>
     * </ul>
     * <p>
     * 副作用（同一关系库事务内）：
     * <ol>
     *   <li>按 {@code tenantId + knowledgeBaseId + documentRef} 查询 projection 主表；不存在则插入；</li>
     *   <li>向 version 表插入新行，状态 {@code PREPARING}，写入计数均为 0；</li>
     *   <li>返回 {@link VectorProjectionVersion} 快照（包含 projection / version 两个 ID）。</li>
     * </ol>
     * 不写 outbox、不写 manifest。vector 库无任何操作。
     * <p>
     * 失败语义：方法由 {@link Transactional} 标注，任何异常都会让 projection 插入 / version 插入
     * 整段回滚；版本号与候选版本不会泄露到关系库。
     *
     * @param reference     投影的业务定位（tenant / knowledgeBase / documentRef）
     * @param specification 投影技术规格（sourceVersion / indexName / embeddingSpaceId）
     * @return 投影版本快照，下游调用方应把它作为后续 {@link #activate}、{@link DefaultVectorProjectionWriter#write} 的输入
     * @throws IllegalArgumentException reference 或 specification 为 null
     */
    @Override
    @Transactional
    public VectorProjectionVersion beginRebuild(VectorProjectionReference reference,
                                                VectorProjectionSpecification specification) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        VectorProjectionEntity projection = projectionMapper.selectOne(new LambdaQueryWrapper<VectorProjectionEntity>()
                .eq(VectorProjectionEntity::getTenantId, reference.tenantId())
                .eq(VectorProjectionEntity::getKnowledgeBaseId, reference.knowledgeBaseId())
                .eq(VectorProjectionEntity::getDocumentRef, reference.documentRef()));
        if (projection == null) {
            projection = new VectorProjectionEntity();
            projection.setId(UUID.randomUUID().toString());
            projection.setTenantId(reference.tenantId());
            projection.setKnowledgeBaseId(reference.knowledgeBaseId());
            projection.setDocumentRef(reference.documentRef());
            projection.setCreatedAt(now);
            projection.setUpdatedAt(now);
            projectionMapper.insert(projection);
        }

        VectorProjectionVersionEntity version = new VectorProjectionVersionEntity();
        version.setId(UUID.randomUUID().toString());
        version.setProjectionId(projection.getId());
        version.setSourceVersion(specification.sourceVersion());
        version.setIndexName(specification.indexName());
        version.setEmbeddingSpaceId(specification.embeddingSpaceId());
        version.setState(VectorProjectionState.PREPARING.name());
        version.setWrittenRecords(0);
        version.setFailedRecords(0);
        version.setCreatedAt(now);
        version.setUpdatedAt(now);
        versionMapper.insert(version);
        return snapshot(projection, version);
    }

    /**
     * 把 {@code READY} 状态的版本切换为 {@code ACTIVE}，同时把旧 ACTIVE 版本切换为 {@code RETIRING} 并入队清理任务。
     * <p>
     * 前置条件：{@code versionId} 必须是 {@code READY} 状态；ACTIVE 状态的版本允许同时存在多个，
     * 但同一 projection 在新版本激活后旧版本必须退场。
     * <p>
     * 副作用（同一关系库事务内）：
     * <ol>
     *   <li>校验候选版本状态为 {@code READY}；</li>
     *   <li>把同 projection 下所有当前 {@code ACTIVE} 版本置为 {@code RETIRING}，并写入
     *       {@code cleanupAfter = now + cleanupDelay}；</li>
     *   <li>为每个被退场的旧版本入队一条 outbox 事件（{@link #CLEANUP_EVENT}，在
     *       {@link #cleanupDueProjections} 中被消费）；</li>
     *   <li>把候选版本置为 {@code ACTIVE}，写入 {@code activatedAt}；</li>
     *   <li>更新 projection 主表的 {@code activeVersionId}。</li>
     * </ol>
     * vector 库本方法内没有任何写入或删除 —— 激活只是关系库的状态切换。
     * <p>
     * 失败语义：方法由 {@link Transactional} 标注；若激活过程中任何 update 失败，
     * 整段事务回滚，候选版本不会变 ACTIVE，旧版本也不会被标 RETIRING / 入队。
     *
     * @param versionId    待激活的版本 ID（必须 {@code READY} 状态）
     * @param cleanupDelay 旧版本延迟清理时间；为 {@code null} 时取 {@link VectorProjectionDaoProperties#cleanupDelay} 兜底
     * @throws IllegalStateException    候选版本不处于 {@code READY} 状态
     * @throws IllegalArgumentException 候选版本不存在
     */
    @Override
    @Transactional
    public void activate(String versionId, Duration cleanupDelay) {
        VectorProjectionVersionEntity candidate = requireVersion(versionId);
        if (stateOf(candidate) != VectorProjectionState.READY) {
            throw new IllegalStateException("only READY projection versions can be activated: " + versionId);
        }
        VectorProjectionEntity projection = requireProjection(candidate.getProjectionId());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime cleanupAfter = now.plus(cleanupDelay == null ? properties.getCleanupDelay() : cleanupDelay);

        List<VectorProjectionVersionEntity> previous = versionMapper.selectList(
                new LambdaQueryWrapper<VectorProjectionVersionEntity>()
                        .eq(VectorProjectionVersionEntity::getProjectionId, projection.getId())
                        .eq(VectorProjectionVersionEntity::getState, VectorProjectionState.ACTIVE.name()));
        for (VectorProjectionVersionEntity old : previous) {
            old.setState(VectorProjectionState.RETIRING.name());
            old.setCleanupAfter(cleanupAfter);
            old.setUpdatedAt(now);
            versionMapper.updateById(old);
            enqueueCleanup(old.getId(), cleanupAfter, now);
        }

        candidate.setState(VectorProjectionState.ACTIVE.name());
        candidate.setActivatedAt(now);
        candidate.setUpdatedAt(now);
        versionMapper.updateById(candidate);
        projection.setActiveVersionId(candidate.getId());
        projection.setUpdatedAt(now);
        projectionMapper.updateById(projection);
    }

    /**
     * 把指定版本标记为 {@code FAILED}，并记录失败原因（自动截断至 1000 字符）。
     * <p>
     * 前置条件：版本不能处于 {@code ACTIVE} 或 {@code CLEANED} —— 这两个状态代表"已被业务使用"
     * 或"已清理完成"，回退到 FAILED 会破坏不变量。
     * <p>
     * 副作用（同一关系库事务内）：
     * <ul>
     *   <li>version 表：{@code state = FAILED}、{@code failureReason = trimReason(reason)}、{@code updatedAt = now}；</li>
     *   <li>不动 projection 主表与 outbox 表 —— 失败的版本不参与清理路径。</li>
     * </ul>
     * 即便状态机已经走到 {@code FAILED} 也允许再次调用，只是幂等地覆盖 {@code failureReason}。
     * <p>
     * 失败语义：方法由 {@link Transactional} 标注；任何异常都会让状态切换整段回滚。
     *
     * @param versionId 投影版本 ID
     * @param reason    失败原因文本（{@code null} / 空白时记为 {@code "unknown"}；超过 1000 字符会被截断）
     * @throws IllegalStateException    版本不存在或版本处于 {@code ACTIVE} / {@code CLEANED} 状态
     * @throws IllegalArgumentException 版本不存在
     */
    @Override
    @Transactional
    public void markFailed(String versionId, String reason) {
        VectorProjectionVersionEntity version = requireVersion(versionId);
        if (stateOf(version) == VectorProjectionState.ACTIVE || stateOf(version) == VectorProjectionState.CLEANED) {
            throw new IllegalStateException("cannot fail projection version in state " + version.getState());
        }
        version.setState(VectorProjectionState.FAILED.name());
        version.setFailureReason(trimReason(reason));
        version.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        versionMapper.updateById(version);
    }

    /**
     * 查询单个投影版本快照 —— 读路径，不开启事务。
     * <p>
     * 读路径：先按主键查 version 行，再回查 projection 行；版本不存在时返回空。
     * 不会自动加载 manifest，manifest 由清理任务按需加载。
     *
     * @param versionId 投影版本 ID
     * @return 投影版本快照；若版本不存在返回 {@link Optional#empty()}
     */
    @Override
    public Optional<VectorProjectionVersion> findVersion(String versionId) {
        VectorProjectionVersionEntity version = versionMapper.selectById(versionId);
        if (version == null) {
            return Optional.empty();
        }
        return Optional.of(snapshot(requireProjection(version.getProjectionId()), version));
    }

    /**
     * 解析指定 tenant / knowledgeBase 下所有 projection 的当前 ACTIVE 版本 ID。
     * <p>
     * 读路径：先按 tenant / knowledgeBase 过滤 projection 主表（仅保留 {@code activeVersionId} 不为空的行），
     * 再去重并返回不可变集合。
     * <p>
     * 业务方约定：检索链路只查询"被解析到的 ACTIVE 版本"对应的向量库，这是"可重建投影"隔离
     * 检索的核心边界 —— 旧版本向量不会被命中。
     *
     * @param tenantId        租户 ID
     * @param knowledgeBaseId 知识库 ID
     * @return 当前 ACTIVE 版本 ID 集合（去重 + 不可变）；该 tenant/kb 下不存在任何 ACTIVE 版本时返回空集合
     */
    @Override
    public java.util.Set<String> activeVersionIds(String tenantId, String knowledgeBaseId) {
        List<VectorProjectionEntity> projections = projectionMapper.selectList(new LambdaQueryWrapper<VectorProjectionEntity>()
                .eq(VectorProjectionEntity::getTenantId, tenantId)
                .eq(VectorProjectionEntity::getKnowledgeBaseId, knowledgeBaseId)
                .isNotNull(VectorProjectionEntity::getActiveVersionId));
        return projections.stream().map(VectorProjectionEntity::getActiveVersionId)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * 清理到期的一次性批量入口 —— 由调用方在外部调度器（Quartz / XXL-Job 等）中周期触发。
     * <p>
     * 调度语义：
     * <ul>
     *   <li>查询 {@code state = RETIRING} 且 {@code cleanupAfter <= now} 的版本，按 {@code maxVersions} 截断；</li>
     *   <li>对每条命中版本调用 {@link #cleanupVersion}；</li>
     *   <li>{@code maxVersions} 由调用方控制每次调度的并发上限（避免一次拖死 vector 库）。</li>
     * </ul>
     * 事务边界：本方法本身不开启事务；每个版本的清理在 {@link #cleanupVersion} 内部分批事务提交。
     * <p>
     * 失败语义：单个版本清理失败不会影响其它版本；失败会被记录到 outbox 的 {@code lastError} 字段，
     * 下次调度会自动重试。
     *
     * @param maxVersions 本次调度最多清理的版本数；{@code <= 0} 时直接返回 0（不做任何查询）
     * @return 成功清理（即 manifest 已删完且状态已切到 {@code CLEANED}）的版本数
     */
    @Override
    public int cleanupDueProjections(int maxVersions) {
        if (maxVersions <= 0) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<VectorProjectionVersionEntity> due = versionMapper.selectList(
                new LambdaQueryWrapper<VectorProjectionVersionEntity>()
                        .eq(VectorProjectionVersionEntity::getState, VectorProjectionState.RETIRING.name())
                        .le(VectorProjectionVersionEntity::getCleanupAfter, now)
                        .last("LIMIT " + maxVersions));
        int cleaned = 0;
        for (VectorProjectionVersionEntity version : due) {
            if (cleanupVersion(version, now)) {
                cleaned++;
            }
        }
        return cleaned;
    }

    /**
     * 由 {@link DefaultVectorProjectionWriter} 回调 —— 把版本状态从 {@code PREPARING} 切到 {@code WRITING}。
     * <p>
     * 副作用：通过 {@link #updateState} 走 CAS 式更新（仅当当前状态等于 {@code expected} 才置为目标状态），
     * 因此并发或重入都是幂等的：第一次成功、后续 no-op。{@link Transactional} 标注保证原子性。
     *
     * @param versionId 投影版本 ID
     * @throws IllegalStateException CAS 失败（即当前状态不是 {@code PREPARING}）
     */
    @Transactional
    void markWriting(String versionId) {
        updateState(versionId, VectorProjectionState.PREPARING, VectorProjectionState.WRITING, null);
    }

    /**
     * 由 {@link DefaultVectorProjectionWriter} 回调 —— 记录单条写入成功事件：
     * <ul>
     *   <li>在 {@code rag_vector_projection_record} 表插一条 vectorId manifest（已存在则跳过，做幂等）；</li>
     *   <li>version 表的 {@code written_records += 1}，{@code updatedAt = now}。</li>
     * </ul>
     * 事务边界：{@link Transactional}，整段原子提交。
     *
     * @param versionId 投影版本 ID
     * @param vectorId  向量库生成的 vectorId（必填非空）
     * @throws IllegalArgumentException vectorId 为 {@code null} 或空白
     */
    @Transactional
    void recordSucceeded(String versionId, String vectorId) {
        if (vectorId == null || vectorId.isBlank()) {
            throw new IllegalArgumentException("vectorId must not be blank");
        }
        Long existing = recordMapper.selectCount(new LambdaQueryWrapper<VectorProjectionRecordEntity>()
                .eq(VectorProjectionRecordEntity::getVersionId, versionId)
                .eq(VectorProjectionRecordEntity::getVectorId, vectorId));
        if (existing == null || existing == 0) {
            VectorProjectionRecordEntity manifest = new VectorProjectionRecordEntity();
            manifest.setId(UUID.randomUUID().toString());
            manifest.setVersionId(versionId);
            manifest.setVectorId(vectorId);
            manifest.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
            recordMapper.insert(manifest);
        }
        versionMapper.update(null, new LambdaUpdateWrapper<VectorProjectionVersionEntity>()
                .eq(VectorProjectionVersionEntity::getId, versionId)
                .setSql("written_records = written_records + 1")
                .set(VectorProjectionVersionEntity::getUpdatedAt, LocalDateTime.now(ZoneOffset.UTC)));
    }

    /**
     * 由 {@link DefaultVectorProjectionWriter} 回调 —— 记录单条写入失败事件：
     * <ul>
     *   <li>version 表的 {@code failed_records += 1}；</li>
     *   <li>{@code failureReason = trimReason(reason)}；</li>
     *   <li>{@code updatedAt = now}。</li>
     * </ul>
     * 失败原因会被 {@link #markReadyIfNoFailure} 用于流完成时的最终判定 —— 一旦
     * {@code failed_records > 0}，版本将被推进到 {@code FAILED} 而不是 {@code READY}。
     * <p>
     * 事务边界：{@link Transactional}。
     *
     * @param versionId 投影版本 ID
     * @param reason    失败原因（{@code null} / 空白时记为 {@code "unknown"}；超过 1000 字符会被截断）
     */
    @Transactional
    void recordFailure(String versionId, String reason) {
        versionMapper.update(null, new LambdaUpdateWrapper<VectorProjectionVersionEntity>()
                .eq(VectorProjectionVersionEntity::getId, versionId)
                .setSql("failed_records = failed_records + 1")
                .set(VectorProjectionVersionEntity::getFailureReason, trimReason(reason))
                .set(VectorProjectionVersionEntity::getUpdatedAt, LocalDateTime.now(ZoneOffset.UTC)));
    }

    /**
     * 由 {@link DefaultVectorProjectionWriter} 回调 —— bulk 写入流正常完成时收尾：
     * 若当前状态仍为 {@code WRITING} 且 {@code failed_records > 0}，则把版本推进到 {@code FAILED}
     * （保留既有 {@code failureReason}）；否则切到 {@code READY}。
     * <p>
     * 幂等性：若状态已经不是 {@code WRITING}（例如已 FAILED 或已被并发回退），直接返回不再切。
     * <p>
     * 事务边界：{@link Transactional}。
     *
     * @param versionId 投影版本 ID
     */
    @Transactional
    void markReadyIfNoFailure(String versionId) {
        VectorProjectionVersionEntity version = requireVersion(versionId);
        if (stateOf(version) != VectorProjectionState.WRITING) {
            return;
        }
        if (version.getFailedRecords() != null && version.getFailedRecords() > 0) {
            markFailed(versionId, version.getFailureReason());
            return;
        }
        version.setState(VectorProjectionState.READY.name());
        version.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        versionMapper.updateById(version);
    }

    /**
     * 由 {@link DefaultVectorProjectionWriter} 回调 —— bulk 写入流异常时把版本置为 {@code FAILED}。
     * <p>
     * 失败原因取自 {@link Throwable#getMessage()}；{@code error} 为 {@code null} 时记为
     * {@code "vector write failed"}。事务边界由 {@link #markFailed} 提供。
     *
     * @param versionId 投影版本 ID
     * @param error     触发失败的异常（{@code null} 时使用默认文本）
     */
    @Transactional
    void failFromWriter(String versionId, Throwable error) {
        markFailed(versionId, error == null ? "vector write failed" : error.getMessage());
    }

    /**
     * 清理单个版本：按 {@link VectorProjectionDaoProperties#getDeleteBatchSize()} 循环读 manifest、
     * 批量删除向量、再批量删除 manifest 行；manifest 读空时调用 {@link #finishCleanup} 收尾。
     * <p>
     * 事务边界：
     * <ul>
     *   <li>删除向量 —— vector 库操作，不在本类事务范围内（外部事务）；</li>
     *   <li>删除 manifest 行 —— 通过独立 {@link TransactionTemplate} 提交，
     *       避免污染当前事务；任一批次失败只回滚该批次，下次调度从剩余 manifest 继续。</li>
     * </ul>
     * 失败语义：捕获 {@link RuntimeException} 后调用 {@link #recordCleanupFailure} 记录 outbox
     * 失败次数 / 错误信息，函数返回 {@code false}，不影响调度循环对其它版本的处理。
     *
     * @param version 当前版本实体（必须 {@code state = RETIRING} 且 {@code cleanupAfter <= now}）
     * @param now     当前 UTC 时间（用于版本状态切到 CLEANED 时写入 updatedAt）
     * @return {@code true} 表示全量清理完成（已切到 CLEANED），{@code false} 表示中途失败
     */
    private boolean cleanupVersion(VectorProjectionVersionEntity version, LocalDateTime now) {
        try {
            while (true) {
                List<VectorProjectionRecordEntity> batch = recordMapper.selectList(
                        new LambdaQueryWrapper<VectorProjectionRecordEntity>()
                                .eq(VectorProjectionRecordEntity::getVersionId, version.getId())
                                .last("LIMIT " + properties.getDeleteBatchSize()));
                if (batch.isEmpty()) {
                    finishCleanup(version, now);
                    return true;
                }
                deleteOperations.deleteByIds(version.getIndexName(), batch.stream()
                        .map(VectorProjectionRecordEntity::getVectorId).toList());
                transactionTemplate.executeWithoutResult(status -> recordMapper.deleteByIds(
                        batch.stream().map(VectorProjectionRecordEntity::getId).toList()));
            }
        } catch (RuntimeException error) {
            recordCleanupFailure(version.getId(), error.getMessage(), now);
            log.warn("vector projection cleanup failed, version={}", version.getId(), error);
            return false;
        }
    }

    /**
     * 清理收尾 —— 把版本状态切到 {@code CLEANED}，并把对应 outbox 事件标记为 {@code PROCESSED}。
     * <p>
     * 事务边界：在独立 {@link TransactionTemplate} 内一次性提交 —— 状态切换与 outbox 收尾必须
     * 同步成功，否则下次调度会重复清理已经处理过的 manifest。
     * <p>
     * 该方法仅由 {@link #cleanupVersion} 在 manifest 已全部删空时调用。
     *
     * @param version 当前版本实体
     * @param now     当前 UTC 时间（写入 updatedAt / processedAt）
     */
    void finishCleanup(VectorProjectionVersionEntity version, LocalDateTime now) {
        transactionTemplate.executeWithoutResult(status -> {
            version.setState(VectorProjectionState.CLEANED.name());
            version.setUpdatedAt(now);
            versionMapper.updateById(version);
            outboxMapper.update(null, new LambdaUpdateWrapper<VectorProjectionOutboxEntity>()
                    .eq(VectorProjectionOutboxEntity::getVersionId, version.getId())
                    .eq(VectorProjectionOutboxEntity::getEventType, CLEANUP_EVENT)
                    .set(VectorProjectionOutboxEntity::getState, OUTBOX_PROCESSED)
                    .set(VectorProjectionOutboxEntity::getProcessedAt, now)
                    .set(VectorProjectionOutboxEntity::getUpdatedAt, now));
        });
    }

    /**
     * 在 outbox 表插入一条待执行的清理任务事件 —— 由 {@link #activate} 在旧 ACTIVE 版本退场时调用。
     * <p>
     * 与 {@link #activate} 的事务边界一致：清理事件入队与状态切换必须一起提交或一起回滚，
     * 否则会出现"版本已 RETIRING 但没有清理任务"的悬挂状态。
     * <p>
     * 注意：本方法只是入队，不依赖任何调度线程；{@link #cleanupDueProjections} 才是真正消费端。
     *
     * @param versionId    待清理的旧版本 ID
     * @param executeAfter 期望执行时间（{@code cleaned_version.cleanup_after}）
     * @param now          当前 UTC 时间（写入 createdAt / updatedAt）
     */
    private void enqueueCleanup(String versionId, LocalDateTime executeAfter, LocalDateTime now) {
        VectorProjectionOutboxEntity event = new VectorProjectionOutboxEntity();
        event.setId(UUID.randomUUID().toString());
        event.setEventType(CLEANUP_EVENT);
        event.setVersionId(versionId);
        event.setState(OUTBOX_PENDING);
        event.setAttempts(0);
        event.setExecuteAfter(executeAfter);
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        outboxMapper.insert(event);
    }

    /**
     * 记录清理失败 —— 累加 outbox 事件的 attempts、写入 lastError，由 {@link #cleanupVersion} 捕获
     * {@link RuntimeException} 时调用。
     * <p>
     * 事务边界：在独立 {@link TransactionTemplate} 内提交，避免与 cleanupVersion 当前批次的隐式事务冲突。
     * 仅更新仍处于 {@code OUTBOX_PENDING} 的事件；已被其它调度并发处理完成后本方法无效果。
     *
     * @param versionId 失败的版本 ID
     * @param error     错误信息（{@code null} / 空白时记为 {@code "unknown"}；超过 1000 字符会被截断）
     * @param now       当前 UTC 时间
     */
    private void recordCleanupFailure(String versionId, String error, LocalDateTime now) {
        transactionTemplate.executeWithoutResult(status -> outboxMapper.update(null,
                new LambdaUpdateWrapper<VectorProjectionOutboxEntity>()
                        .eq(VectorProjectionOutboxEntity::getVersionId, versionId)
                        .eq(VectorProjectionOutboxEntity::getEventType, CLEANUP_EVENT)
                        .eq(VectorProjectionOutboxEntity::getState, OUTBOX_PENDING)
                        .setSql("attempts = attempts + 1")
                        .set(VectorProjectionOutboxEntity::getLastError, trimReason(error))
                        .set(VectorProjectionOutboxEntity::getUpdatedAt, now)));
    }

    /**
     * CAS 式状态转移 —— 仅当当前状态等于 {@code expected} 时才置为 {@code target}。
     * <p>
     * 重要不变量：状态机的转移只能发生在明确合法的方向上。该方法把转移条件做成 SQL
     * WHERE 子句，避免读到当前状态再写之间被并发覆盖。
     *
     * @param versionId 投影版本 ID
     * @param expected  当前状态（必须匹配）
     * @param target    目标状态
     * @param reason    失败原因（仅用于失败转移，可为 {@code null}）
     * @throws IllegalStateException CAS 失败（即当前状态不是 {@code expected}）
     */
    private void updateState(String versionId, VectorProjectionState expected, VectorProjectionState target, String reason) {
        int updated = versionMapper.update(null, new LambdaUpdateWrapper<VectorProjectionVersionEntity>()
                .eq(VectorProjectionVersionEntity::getId, versionId)
                .eq(VectorProjectionVersionEntity::getState, expected.name())
                .set(VectorProjectionVersionEntity::getState, target.name())
                .set(VectorProjectionVersionEntity::getFailureReason, reason)
                .set(VectorProjectionVersionEntity::getUpdatedAt, LocalDateTime.now(ZoneOffset.UTC)));
        if (updated != 1) {
            throw new IllegalStateException("invalid projection version state transition for " + versionId);
        }
    }

    /**
     * 按主键查版本 —— 不存在时抛 {@link IllegalArgumentException}（视作用户传错，非系统状态）。
     *
     * @param versionId 投影版本 ID
     * @return 对应的版本实体
     * @throws IllegalArgumentException 版本不存在
     */
    private VectorProjectionVersionEntity requireVersion(String versionId) {
        VectorProjectionVersionEntity version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new IllegalArgumentException("projection version does not exist: " + versionId);
        }
        return version;
    }

    /**
     * 按主键查 projection —— 不存在时抛 {@link IllegalStateException}（视作数据不完整，系统级错误）。
     *
     * @param projectionId projection 主表 ID
     * @return 对应的 projection 实体
     * @throws IllegalStateException projection 不存在
     */
    private VectorProjectionEntity requireProjection(String projectionId) {
        VectorProjectionEntity projection = projectionMapper.selectById(projectionId);
        if (projection == null) {
            throw new IllegalStateException("projection does not exist: " + projectionId);
        }
        return projection;
    }

    /**
     * 把关系库实体映射成对外的快照 VO —— 同时装填 reference / specification / 状态 / 计数 / cleanup 时间 / 失败原因。
     * <p>
     * 数字字段容忍 {@code null}（{@link #nullToZero}）；时间字段容忍 {@code null}（{@link #toInstant}）。
     *
     * @param projection projection 主表实体
     * @param version    version 表实体
     * @return 投影版本快照
     */
    private VectorProjectionVersion snapshot(VectorProjectionEntity projection, VectorProjectionVersionEntity version) {
        return new VectorProjectionVersion(projection.getId(), version.getId(),
                new VectorProjectionReference(projection.getTenantId(), projection.getKnowledgeBaseId(), projection.getDocumentRef()),
                new VectorProjectionSpecification(version.getSourceVersion(), version.getIndexName(), version.getEmbeddingSpaceId()),
                stateOf(version), nullToZero(version.getWrittenRecords()), nullToZero(version.getFailedRecords()),
                toInstant(version.getCleanupAfter()), version.getFailureReason());
    }

    /**
     * 把 version 表里的字符串状态反序列化为枚举。
     *
     * @param version version 表实体
     * @return 投影版本状态枚举
     */
    private static VectorProjectionState stateOf(VectorProjectionVersionEntity version) {
        return VectorProjectionState.valueOf(version.getState());
    }

    /**
     * {@code Integer} 字段为 {@code null} 时规范化为 0 —— 避免对外暴露包装类的坑。
     *
     * @param value 原始整数值
     * @return 非 null 整数
     */
    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 把数据库的 {@link LocalDateTime} 转换为 UTC {@link Instant} —— 便于跨时区业务消费。
     *
     * @param value 原始时间字段
     * @return UTC 时间戳；{@code value} 为 {@code null} 时返回 {@code null}
     */
    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    /**
     * 把失败原因规范化为可持久化字符串 —— 空值兜底为 {@code "unknown"}，超长文本截断至 1000 字符。
     *
     * @param value 原始原因文本
     * @return 永远非 null、长度 ≤ 1000 的失败原因
     */
    private static String trimReason(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
