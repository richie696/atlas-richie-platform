package cn.richie696.component.vector.projection.config;

import cn.richie696.component.vector.projection.VectorProjectionWriter;
import cn.richie696.component.vector.projection.impl.DefaultVectorProjectionService;
import cn.richie696.component.vector.projection.impl.DefaultVectorProjectionWriter;
import cn.richie696.component.vector.projection.persistence.VectorProjectionMapper;
import cn.richie696.component.vector.projection.persistence.VectorProjectionOutboxMapper;
import cn.richie696.component.vector.projection.persistence.VectorProjectionRecordMapper;
import cn.richie696.component.vector.projection.persistence.VectorProjectionVersionMapper;
import cn.richie696.component.vector.service.VectorBulkOperations;
import cn.richie696.component.vector.service.VectorRecordDeleteOperations;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 向量投影 DAO 插件的 Spring Boot 自动装配入口。
 * <p>
 * 本类是 {@code vector-projection-dao} 模块暴露给业务侧的唯一自动配置类，
 * 自身不做任何业务逻辑，只负责在满足条件时把投影状态机所需的全部 Bean 装配到 Spring 容器中。
 * 装配产生的两个核心 Bean 是：
 * <ul>
 *   <li>{@link DefaultVectorProjectionService} —— 投影版本状态机 + 延迟清理 + 关系库事务编排；</li>
 *   <li>{@link DefaultVectorProjectionWriter} —— 把 bulk 写事件投影为版本状态和 vectorId manifest。</li>
 * </ul>
 * <p>
 * 激活条件（缺一不可）：
 * <ol>
 *   <li>类路径下存在 MyBatis-Plus {@link BaseMapper}（{@link ConditionalOnClass}），保证
 *       {@code @MapperScan} 扫描的 4 个 Mapper 可被注册；</li>
 *   <li>Spring 容器中已经存在 {@link VectorBulkOperations}、{@link VectorRecordDeleteOperations}、
 *       {@link PlatformTransactionManager} 三个 Bean（{@link ConditionalOnBean}），即
 *       vector 组件本身 + 业务侧数据库事务管理器已就绪；</li>
 *   <li>YAML 配置 {@code platform.component.vector.projection.enabled=true}
 *       （{@link ConditionalOnProperty}），默认关闭；</li>
 *   <li>应用选择启用 {@link VectorProjectionDaoProperties}（{@link EnableConfigurationProperties}）。</li>
 * </ol>
 * <p>
 * 不变量：
 * <ul>
 *   <li>本类不会自动建表 —— 启用前必须先执行 {@code schema/vector-projection-schema.sql}；</li>
 *   <li>本类不会启动任何调度线程 —— {@code cleanupDueProjections(...)} 必须由调用方通过
 *       Quartz / XXL-Job 等外部调度器周期触发；</li>
 *   <li>本类只接管投影相关的 4 张表，对业务文档表无侵入。</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
@AutoConfiguration
@ConditionalOnClass(BaseMapper.class)
@ConditionalOnBean({VectorBulkOperations.class, VectorRecordDeleteOperations.class, PlatformTransactionManager.class})
@ConditionalOnProperty(prefix = "platform.component.vector.projection", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(VectorProjectionDaoProperties.class)
@MapperScan(basePackageClasses = VectorProjectionMapper.class)
public class VectorProjectionDaoAutoConfiguration {

    /**
     * 装配投影状态机核心 Bean —— {@link DefaultVectorProjectionService}。
     * <p>
     * 同时实现 {@link cn.richie696.component.vector.projection.VectorProjectionLifecycleService}
     * （生命周期编排）、{@link cn.richie696.component.vector.projection.VectorProjectionCleanupService}
     * （延迟清理调度入口）和 {@link cn.richie696.component.vector.knowledge.ActiveProjectionVersionResolver}
     * （按 tenant / knowledgeBase 解析当前 ACTIVE 版本）三个接口。
     * <p>
     * 激活条件：当前 Spring 容器中尚未存在 {@link DefaultVectorProjectionService} 类型的 Bean
     * （{@link ConditionalOnMissingBean}），业务方可通过自定义 Bean 整体替换默认实现。
     * <p>
     * 注入依赖（按依赖角色分组）：
     * <ul>
     *   <li>4 个关系库 Mapper —— 投影、版本、manifest、outbox；</li>
     *   <li>{@link VectorRecordDeleteOperations} —— 清理阶段按 manifest 批量删除向量；</li>
     *   <li>{@link VectorProjectionDaoProperties} —— cleanupDelay / deleteBatchSize；</li>
     *   <li>{@link PlatformTransactionManager} —— 包装为 {@link TransactionTemplate}，
     *       供清理批次使用（避免与当前事务传播冲突）。</li>
     * </ul>
     *
     * @param projectionMapper   projection 主表 Mapper（按 tenant+knowledgeBase+documentRef 唯一定位一条记录）
     * @param versionMapper      projection version 表 Mapper（状态机主表）
     * @param recordMapper       vectorId manifest 表 Mapper（清理阶段按 versionId 批量读取）
     * @param outboxMapper       outbox 事件表 Mapper（清理任务入队 / 完成标记）
     * @param deleteOperations   vector 库删除操作抽象
     * @param properties         插件配置（cleanupDelay / deleteBatchSize）
     * @param transactionManager 业务侧事务管理器，用于构造独立的 {@link TransactionTemplate}
     * @return 已构造且依赖完备的默认实现
     */
    @Bean
    @ConditionalOnMissingBean(DefaultVectorProjectionService.class)
    public DefaultVectorProjectionService vectorProjectionService(VectorProjectionMapper projectionMapper,
                                                                  VectorProjectionVersionMapper versionMapper,
                                                                  VectorProjectionRecordMapper recordMapper,
                                                                  VectorProjectionOutboxMapper outboxMapper,
                                                                  VectorRecordDeleteOperations deleteOperations,
                                                                  VectorProjectionDaoProperties properties,
                                                                  PlatformTransactionManager transactionManager) {
        return new DefaultVectorProjectionService(projectionMapper, versionMapper, recordMapper, outboxMapper,
                deleteOperations, properties, new TransactionTemplate(transactionManager));
    }

    /**
     * 装配 bulk 写入侧的 Bean —— {@link DefaultVectorProjectionWriter}。
     * <p>
     * 该 Bean 把 {@link VectorBulkOperations#upsertAll(String, reactor.core.publisher.Flux)} 的
     * 事件流投影为：版本状态切换（{@code PREPARING → WRITING → READY} / {@code FAILED}）以及
     * 每条记录的 {@code vectorId} 持久化到 manifest 表。
     * <p>
     * 激活条件：当前 Spring 容器中尚未存在任何 {@link cn.richie696.component.vector.projection.VectorProjectionWriter}
     * 类型的 Bean（{@link ConditionalOnMissingBean}），业务方可以替换为基于其它写入管线的自定义实现。
     * <p>
     * 注入依赖：
     * <ul>
     *   <li>{@link DefaultVectorProjectionService} —— 通过容器注入的本装配类产出的状态机 Bean；</li>
     *   <li>{@link VectorBulkOperations} —— vector 组件提供的 bulk 写入抽象（任何 Provider 都实现）。</li>
     * </ul>
     *
     * @param service              状态机 Bean（由 {@link #vectorProjectionService} 装配）
     * @param vectorBulkOperations bulk 写入抽象（嵌入 / 检索过程不感知具体 provider）
     * @return 把 bulk 事件投影到状态机和 manifest 的默认实现
     */
    @Bean
    @ConditionalOnMissingBean(VectorProjectionWriter.class)
    public VectorProjectionWriter vectorProjectionWriter(DefaultVectorProjectionService service,
                                                         VectorBulkOperations vectorBulkOperations) {
        return new DefaultVectorProjectionWriter(service, vectorBulkOperations);
    }
}
