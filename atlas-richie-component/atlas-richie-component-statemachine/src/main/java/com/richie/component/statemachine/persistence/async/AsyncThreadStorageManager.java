package com.richie.component.statemachine.persistence.async;

import com.richie.component.statemachine.config.StateMachineProperties;
import com.richie.component.statemachine.config.properties.ThreadPoolConfig;
import com.richie.component.statemachine.event.StateSyncKey;
import com.richie.component.statemachine.persistence.dao.entity.StateMachineStateCurrent;
import com.richie.component.statemachine.persistence.dao.entity.StateMachineStateHistory;
import com.richie.component.statemachine.persistence.dao.mapper.StateMachineStateCurrentMapper;
import com.richie.component.statemachine.persistence.dao.mapper.StateMachineStateHistoryMapper;
import com.richie.component.statemachine.storage.StateHistory;
import com.richie.component.statemachine.storage.StateStorage;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.richie.component.statemachine.persistence.async.AsyncThreadStorageConstants.HISTORY_KEY_SEPARATOR;
import static com.richie.component.statemachine.persistence.async.AsyncThreadStorageConstants.SHUTDOWN_WAIT_SECONDS;

/**
 * 异步线程池数据库复制服务
 * <p>
 * 当 Redis Stream 不可用时（如 Redis 4.x 及以下版本、云PAAS Redis不支持），使用异步线程池进行数据库持久化。
 * 通过共享的线程安全缓冲区和批量刷新机制，实现批量写入数据库，提升性能。
 * <p>
 * 核心特性：
 * <ul>
 *   <li>虚拟线程支持：使用 Java 21 虚拟线程，适合 I/O 密集型任务，提供更好的资源利用率</li>
 *   <li>共享缓冲区：所有虚拟线程共享一个线程安全的缓冲区，支持跨线程批量累积</li>
 *   <li>批量写入：达到批量大小时自动刷写</li>
 *   <li>紧急刷写：缓冲区达到最大容量时立即刷写，避免溢出</li>
 *   <li>优雅关闭：服务关闭时自动刷写剩余消息</li>
 * </ul>
 * <p>
 * <strong>虚拟线程优势：</strong>
 * <ul>
 *   <li>轻量级：可以创建数百万个虚拟线程，不受平台线程数量限制</li>
 *   <li>I/O 优化：在 I/O 阻塞时自动释放平台线程，提高资源利用率</li>
 *   <li>简化管理：无需手动管理线程池大小，由 JVM 自动调度</li>
 * </ul>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "platform.component.statemachine", name = "storage-type", havingValue = "ASYNC_THREAD")
@ConditionalOnProperty(prefix = "platform.component.statemachine.thread-pool", name = "enabled", havingValue = "true")
@MapperScan("com.richie.component.statemachine.persistence.dao.mapper")
public class AsyncThreadStorageManager {


    // ==================== 依赖注入 ====================

    private final StateStorage stateStorage;
    private final StateMachineStateCurrentMapper currentStateMapper;
    private final StateMachineStateHistoryMapper historyMapper;
    private final ThreadPoolConfig config;
    private final DataSource dataSource;

    // ==================== 线程池 ====================

    /**
     * 异步执行线程池（虚拟线程）
     */
    private final ExecutorService asyncExecutor;

    /**
     * 定时刷新调度器
     * <p>
     * 定期检查缓冲区，如果有数据则触发刷写，确保数据及时持久化。
     * 使用单线程虚拟线程调度器。
     */
    private final ScheduledExecutorService flushScheduler;

    // ==================== 缓冲区 ====================

    /**
     * 共享的线程安全缓冲区
     * <p>
     * 所有虚拟线程共享此缓冲区，用于累积待同步的状态同步键。
     * 使用 {@link ConcurrentLinkedQueue} 保证线程安全。
     */
    private final ConcurrentLinkedQueue<StateSyncKey> sharedBuffer = new ConcurrentLinkedQueue<>();

    /**
     * 共享的计数器
     * <p>
     * 记录当前缓冲区中的消息数量，用于判断是否需要触发批量刷写。
     * 使用 {@link AtomicInteger} 保证线程安全。
     */
    private final AtomicInteger sharedCounter = new AtomicInteger(0);

    /**
     * 批量刷写锁
     * <p>
     * 用于保证批量刷写的原子性，避免多个线程同时触发刷写导致数据丢失。
     */
    private final Object flushLock = new Object();

    // ==================== 数据库类型 ====================

    /**
     * 数据库类型
     * <p>
     * 用于根据不同的数据库类型选择不同的批量插入或更新策略。
     * 通过 DataSource 自动检测。
     * </p>
     */
    private DbType dbType;

    // ==================== 状态控制 ====================

    /**
     * 是否正在关闭
     */
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

    /**
     * 构造函数
     *
     * @param stateStorage 状态存储
     * @param currentStateMapper 当前状态映射
     * @param historyMapper 状态历史映射
     * @param properties 状态机属性
     * @param dataSource 数据源（用于自动检测数据库类型）
     */
    public AsyncThreadStorageManager(
            StateStorage stateStorage,
            StateMachineStateCurrentMapper currentStateMapper,
            StateMachineStateHistoryMapper historyMapper,
            StateMachineProperties properties,
            DataSource dataSource) {
        this.stateStorage = stateStorage;
        this.currentStateMapper = currentStateMapper;
        this.historyMapper = historyMapper;
        this.config = properties.getThreadPool();
        this.dataSource = dataSource;

        // 初始化虚拟线程执行器
        this.asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();

        // 初始化定时刷新调度器（单线程虚拟线程）
        this.flushScheduler = Executors.newScheduledThreadPool(1, Thread.ofVirtual()
                .name("statemachine-flush-scheduler", 0)
                .factory());

        log.info("异步线程池数据库复制服务已启动（使用虚拟线程），批量大小: {}, 缓冲区容量: {}, 刷新间隔: {}ms",
                config.getBatchSize(), config.getBufferCapacity(), config.getFlushIntervalMs());
    }

    /**
     * 初始化数据库类型
     * <p>
     * 从 DataSource 自动检测数据库类型，通过 DatabaseMetaData 获取数据库产品名称。
     * </p>
     */
    @PostConstruct
    public void initDbType() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String databaseProductName = metaData.getDatabaseProductName().toUpperCase();

            // 根据数据库产品名称映射到 MyBatis-Plus 的 DbType
            if (databaseProductName.contains("MYSQL")) {
                this.dbType = DbType.MYSQL;
            } else if (databaseProductName.contains("POSTGRESQL")) {
                this.dbType = DbType.POSTGRE_SQL;
            } else if (databaseProductName.contains("ORACLE")) {
                this.dbType = DbType.ORACLE;
            } else {
                this.dbType = null;
                log.warn("不支持的数据库类型: {}，将使用查询+分离方式", databaseProductName);
                return;
            }

            log.info("状态机数据库复制服务已初始化，自动检测数据库类型: {} (产品名称: {})",
                    dbType, databaseProductName);
        } catch (Exception e) {
            log.warn("无法从 DataSource 自动检测数据库类型，将使用查询+分离方式: {}", e.getMessage());
            this.dbType = null;
        }

        // 启动定时刷新任务
        startScheduledFlush();
    }

    /**
     * 启动定时刷新任务
     * <p>
     * 定期检查共享缓冲区，如果有数据则触发刷写，确保数据及时持久化。
     * 即使未达到批量大小，也会定期刷写缓冲区中的数据。
     * </p>
     */
    private void startScheduledFlush() {
        long intervalMs = config.getFlushIntervalMs();
        flushScheduler.scheduleWithFixedDelay(
                () -> {
                    try {
                        // 检查缓冲区是否有数据
                        if (!sharedBuffer.isEmpty() && !isShuttingDown.get()) {
                            log.debug("定时刷新任务触发，检查缓冲区: size={}", sharedBuffer.size());
                            doFlushBatch();
                        }
                    } catch (Exception e) {
                        log.error("定时刷新任务执行失败", e);
                    }
                },
                intervalMs,
                intervalMs,
                TimeUnit.MILLISECONDS
        );
        log.debug("定时刷新任务已启动，刷新间隔: {}ms", intervalMs);
    }

    // ==================== 公共 API ====================

    /**
     * 异步提交状态同步任务
     * <p>
     * 将状态同步键添加到共享缓冲区，当达到批量大小时自动刷写。
     *
     *
     * @param stateMachineName 状态机名称
     * @param businessId       业务 ID
     */
    public void submitSync(String stateMachineName, Long businessId) {
        if (isShuttingDown.get()) {
            log.warn("服务正在关闭，跳过状态同步: stateMachine={}, businessId={}", stateMachineName, businessId);
            return;
        }

        asyncExecutor.execute(() -> processSyncTask(stateMachineName, businessId));
    }

    /**
     * 处理状态同步任务
     *
     * @param stateMachineName 状态机名称
     * @param businessId       业务 ID
     */
    private void processSyncTask(String stateMachineName, Long businessId) {
        try {
            // 添加到共享缓冲区
            StateSyncKey syncKey = new StateSyncKey(stateMachineName, businessId);
            sharedBuffer.offer(syncKey);

            // 增加计数器并检查是否需要刷写
            int currentCount = sharedCounter.incrementAndGet();
            int batchSize = config.getBatchSize();
            int bufferCapacity = config.getBufferCapacity();

            if (currentCount >= bufferCapacity) {
                log.warn("缓冲区达到最大容量，触发紧急刷写: currentCount={}, bufferCapacity={}", currentCount, bufferCapacity);
                doFlushBatch();
            } else if (currentCount >= batchSize) {
                doFlushBatch();
            }
        } catch (Exception e) {
            log.error("提交状态同步任务失败: stateMachine={}, businessId={}", stateMachineName, businessId, e);
        }
    }

    // ==================== 批量刷写 ====================

    /**
     * 批量刷写到数据库
     * <p>
     * 从共享缓冲区读取同步键，从 Redis 读取最新状态和历史记录，然后批量写入数据库。
     * 使用锁保证批量刷写的原子性，避免多个线程同时触发刷写。
     *
     */
    private void doFlushBatch() {
        if (isShuttingDown.get()) {
            return;
        }

        // 使用锁保证批量刷写的原子性
        synchronized (flushLock) {
            List<StateSyncKey> syncKeys = drainBuffer();
            if (syncKeys.isEmpty()) {
                return;
            }

            try {
                log.debug("开始批量刷写状态同步: count={}", syncKeys.size());

                SyncData syncData = readSyncDataFromRedis(syncKeys);
                writeSyncDataToDatabase(syncData);

                log.debug("批量刷写状态同步完成: currentCount={}, historyCount={}",
                        syncData.currentStates().size(), syncData.histories().size());
            } catch (Exception e) {
                log.error("批量刷写状态同步失败", e);
            }
        }
    }

    /**
     * 清空共享缓冲区并返回所有同步键
     * <p>
     * 注意：此方法必须在 {@link #flushLock} 锁保护下调用。
     *
     * @return 同步键列表
     */
    private List<StateSyncKey> drainBuffer() {
        List<StateSyncKey> syncKeys = new ArrayList<>();
        StateSyncKey syncKey;
        while ((syncKey = sharedBuffer.poll()) != null) {
            syncKeys.add(syncKey);
        }

        // 重置计数器
        sharedCounter.set(0);

        return syncKeys;
    }

    /**
     * 同步数据容器
     *
     * @param currentStates 当前状态列表
     * @param histories 历史记录列表
     */
    private record SyncData(
            List<StateMachineStateCurrent> currentStates,
            List<StateMachineStateHistory> histories
    ) {
    }

    /**
     * 从 Redis 读取同步数据
     *
     * @param syncKeys 同步键列表
     * @return 同步数据
     */
    private SyncData readSyncDataFromRedis(List<StateSyncKey> syncKeys) {
        List<StateMachineStateCurrent> currentStates = new ArrayList<>();
        List<StateMachineStateHistory> histories = new ArrayList<>();

        for (StateSyncKey syncKey : syncKeys) {
            String stateMachineName = syncKey.stateMachineName();
            Long businessId = syncKey.businessId();

            LocalDateTime businessTime = readAndConvertHistories(stateMachineName, businessId, histories);
            readAndConvertCurrentState(stateMachineName, businessId, businessTime, currentStates);
        }

        return new SyncData(currentStates, histories);
    }

    /**
     * 读取并转换历史记录
     *
     * @param stateMachineName 状态机名称
     * @param businessId 业务 ID
     * @param historyBatch 历史记录批量
     * @return 业务时间（最新历史记录的时间）
     */
    private LocalDateTime readAndConvertHistories(String stateMachineName, Long businessId,
                                                  List<StateMachineStateHistory> historyBatch) {
        List<StateHistory> histories = stateStorage.getStateHistory(stateMachineName, businessId);
        if (histories == null || histories.isEmpty()) {
            return null;
        }

        int maxHistoryToSync = Math.min(config.getBatchSize(), histories.size());
        LocalDateTime latestTime = null;

        for (int i = 0; i < maxHistoryToSync; i++) {
            StateHistory history = histories.get(i);
            if (history.getCreateTime() == null) {
                continue;
            }

            historyBatch.add(convertToHistoryEntity(history));
            if (latestTime == null || history.getCreateTime().isAfter(latestTime)) {
                latestTime = history.getCreateTime();
            }
        }

        return latestTime;
    }

    /**
     * 读取并转换当前状态
     *
     * @param stateMachineName 状态机名称
     * @param businessId 业务 ID
     * @param businessTime 业务时间
     * @param currentStateBatch 当前状态列表
     */
    private void readAndConvertCurrentState(String stateMachineName, Long businessId,
                                           LocalDateTime businessTime,
                                           List<StateMachineStateCurrent> currentStateBatch) {
        String currentState = stateStorage.getCurrentState(stateMachineName, businessId);
        if (currentState == null) {
            return;
        }

        LocalDateTime updatedAt = businessTime != null ? businessTime : LocalDateTime.now();
        currentStateBatch.add(StateMachineStateCurrent.builder()
                .stateMachine(stateMachineName)
                .businessId(businessId)
                .currentState(currentState)
                .seq(resolveLatestSeq(stateMachineName, businessId))
                .updatedAt(updatedAt)
                .build());
    }

    /**
     * 转换历史记录为数据库实体
     *
     * @param history 历史记录
     * @return 转换后的数据库实体
     */
    private StateMachineStateHistory convertToHistoryEntity(StateHistory history) {
        return StateMachineStateHistory.builder()
                .stateMachine(history.getStateMachineName())
                .businessId(history.getBusinessId())
                .prevState(history.getFromState())
                .currState(history.getToState())
                .eventName(history.getEvent())
                .seq(history.getSeq())
                .occurredAt(history.getCreateTime())
                .build();
    }

    /**
     * 将同步数据写入数据库
     *
     * @param syncData 同步数据
     */
    private void writeSyncDataToDatabase(SyncData syncData) {
        if (!syncData.currentStates().isEmpty()) {
            writeCurrentStateBatch(syncData.currentStates());
        }
        if (!syncData.histories().isEmpty()) {
            writeHistoryBatch(syncData.histories());
        }
    }

    // ==================== 数据库写入 ====================

    /**
     * 批量写入当前状态
     * <p>
     * 根据数据库类型选择不同的批量插入或更新策略：
     * <ul>
     *   <li>MySQL: 使用 {@code INSERT ... ON DUPLICATE KEY UPDATE}</li>
     *   <li>PostgreSQL: 使用 {@code INSERT ... ON CONFLICT ... DO UPDATE}</li>
     *   <li>Oracle: 使用 {@code MERGE INTO}</li>
     *   <li>其他数据库: 回退到查询+分离的方式</li>
     * </ul>
     * </p>
     *
     * @param currentStateBatch 要写入的当前状态列表
     */
    private void writeCurrentStateBatch(List<StateMachineStateCurrent> currentStateBatch) {
        if (currentStateBatch.isEmpty()) {
            return;
        }

        // 根据数据库类型选择不同的实现
        if (dbType == null) {
            // 如果数据库类型未初始化，回退到查询+分离方式
            log.warn("数据库类型未初始化，使用查询+分离方式批量写入");
            writeCurrentStateBatchWithQuery(currentStateBatch);
            return;
        }

        try {
            switch (dbType) {
                case MYSQL -> currentStateMapper.insertOrUpdateBatchForMysql(currentStateBatch);
                case POSTGRE_SQL -> currentStateMapper.insertOrUpdateBatchForPostgresql(currentStateBatch);
                case ORACLE -> currentStateMapper.insertOrUpdateBatchForOracle(currentStateBatch);
                default -> {
                    log.warn("数据库类型 {} 不支持批量插入或更新，使用查询+分离方式", dbType);
                    writeCurrentStateBatchWithQuery(currentStateBatch);
                }
            }
        } catch (Exception e) {
            log.error("批量插入或更新失败，回退到查询+分离方式: dbType={}", dbType, e);
            writeCurrentStateBatchWithQuery(currentStateBatch);
        }
    }

    /**
     * 使用查询+分离方式批量写入当前状态（兼容所有数据库）
     * <p>
     * 当数据库不支持批量插入或更新语法时，使用此方法作为兜底方案。
     * </p>
     *
     * @param currentStateBatch 要写入的当前状态列表
     */
    private void writeCurrentStateBatchWithQuery(List<StateMachineStateCurrent> currentStateBatch) {
        // 查询已存在的记录
        List<String> stateMachineNames = extractDistinct(currentStateBatch, StateMachineStateCurrent::getStateMachine);
        List<Long> businessIds = extractDistinct(currentStateBatch, StateMachineStateCurrent::getBusinessId);

        List<StateMachineStateCurrent> existingList = currentStateMapper.selectList(
                new LambdaQueryWrapper<StateMachineStateCurrent>()
                        .in(StateMachineStateCurrent::getStateMachine, stateMachineNames)
                        .in(StateMachineStateCurrent::getBusinessId, businessIds)
        );

        Set<String> existingKeys = existingList.stream()
                .map(current -> current.getStateMachine() + ":" + current.getBusinessId())
                .collect(Collectors.toSet());

        // 分离插入和更新
        List<StateMachineStateCurrent> toInsert = new ArrayList<>();
        List<StateMachineStateCurrent> toUpdate = new ArrayList<>();

        for (StateMachineStateCurrent current : currentStateBatch) {
            String key = current.getStateMachine() + ":" + current.getBusinessId();
            if (existingKeys.contains(key)) {
                toUpdate.add(current);
            } else {
                toInsert.add(current);
            }
        }

        // 批量插入
        if (!toInsert.isEmpty()) {
            for (StateMachineStateCurrent current : toInsert) {
                currentStateMapper.insert(current);
            }
        }

        // 批量更新
        if (!toUpdate.isEmpty()) {
            for (StateMachineStateCurrent current : toUpdate) {
                currentStateMapper.update(current,
                        new LambdaQueryWrapper<StateMachineStateCurrent>()
                                .eq(StateMachineStateCurrent::getStateMachine, current.getStateMachine())
                                .eq(StateMachineStateCurrent::getBusinessId, current.getBusinessId())
                                .and(current.getSeq() != null,
                                        w -> w.isNull(StateMachineStateCurrent::getSeq)
                                                .or()
                                                .lt(StateMachineStateCurrent::getSeq, current.getSeq()))
                );
            }
        }
    }

    /**
     * 批量写入历史记录
     *
     * @param historyBatch 要写入的历史记录列表
     */
    private void writeHistoryBatch(List<StateMachineStateHistory> historyBatch) {
        Set<String> existingKeys = queryExistingHistoryKeys(historyBatch);
        List<StateMachineStateHistory> toInsert = historyBatch.stream()
                .filter(history -> !existingKeys.contains(buildHistoryKey(history)))
                .toList();

        insertHistoriesWithDeduplication(toInsert);
    }

    /**
     * 查询已存在的历史记录键
     *
     * @param batch 历史记录列表
     * @return 已存在历史记录键
     */
    private Set<String> queryExistingHistoryKeys(List<StateMachineStateHistory> batch) {
        List<String> stateMachineNames = extractDistinct(batch, StateMachineStateHistory::getStateMachine);
        List<Long> businessIds = extractDistinct(batch, StateMachineStateHistory::getBusinessId);

        List<StateMachineStateHistory> existingList = historyMapper.selectList(
                new LambdaQueryWrapper<StateMachineStateHistory>()
                        .in(StateMachineStateHistory::getStateMachine, stateMachineNames)
                        .in(StateMachineStateHistory::getBusinessId, businessIds)
        );

        return existingList.stream()
                .map(this::buildHistoryKey)
                .collect(Collectors.toSet());
    }

    /**
     * 构建历史记录键
     *
     * @param history 历史记录
     * @return 历史记录键
     */
    private String buildHistoryKey(StateMachineStateHistory history) {
        Object uniquePart = history.getSeq() != null ? history.getSeq() : history.getOccurredAt();
        return history.getStateMachine() + HISTORY_KEY_SEPARATOR
                + history.getBusinessId() + HISTORY_KEY_SEPARATOR
                + uniquePart;
    }

    /**
     * 插入历史记录（带去重处理）
     *
     * @param toInsert 要插入的历史记录列表
     */
    private void insertHistoriesWithDeduplication(List<StateMachineStateHistory> toInsert) {
        for (StateMachineStateHistory history : toInsert) {
            historyMapper.insert(history);
        }
    }

    private Long resolveLatestSeq(String stateMachineName, Long businessId) {
        List<StateHistory> histories = stateStorage.getStateHistory(stateMachineName, businessId);
        if (histories == null || histories.isEmpty()) {
            return 0L;
        }
        return histories.stream()
                .map(StateHistory::getSeq)
                .filter(java.util.Objects::nonNull)
                .max(Long::compareTo)
                .orElse(0L);
    }

    // ==================== 工具方法 ====================

    /**
     * 提取去重后的列表
     *
     * @param list 结果集
     * @param mapper 映射函数
     * @param <T> 列表元素类型
     * @param <R> 映射结果类型
     * @return 去重后的列表
     */
    private <T, R> List<R> extractDistinct(List<T> list, Function<T, R> mapper) {
        return list.stream()
                .map(mapper)
                .distinct()
                .collect(Collectors.toList());
    }


    // ==================== 生命周期管理 ====================

    /**
     * 服务关闭时刷写剩余的缓冲区
     */
    @PreDestroy
    public void shutdown() {
        try {
            log.info("开始关闭异步线程池数据库复制服务");

            isShuttingDown.set(true);
            shutdownExecutors();

            // 刷写剩余的缓冲区
            doFlushBatch();

            log.info("异步线程池数据库复制服务已关闭");
        } catch (Exception e) {
            log.error("关闭异步线程池数据库复制服务失败", e);
        }
    }

    /**
     * 关闭所有执行器
     */
    private void shutdownExecutors() {
        shutdownExecutor(flushScheduler, "定时刷新调度器");
        shutdownExecutor(asyncExecutor, "异步执行线程池");
    }

    /**
     * 关闭执行器（通用方法）
     *
     * @param executor 执行器
     * @param executorName 执行器名称（用于日志）
     */
    private void shutdownExecutor(ExecutorService executor, String executorName) {
        if (executor == null || executor.isShutdown()) {
            return;
        }

        executor.shutdown();

        try {
            if (!executor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("{}未能及时停止，强制关闭", executorName);
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

}
