package com.richie.component.statemachine.persistence.stream;

import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.redis.manage.CacheLock;
import com.richie.component.statemachine.config.StateMachineDefinition;
import com.richie.component.statemachine.config.StateMachineDefinitionRegistry;
import com.richie.component.statemachine.config.StateMachineProperties;
import com.richie.component.statemachine.config.properties.RedisStreamConfig;
import com.richie.component.statemachine.persistence.dao.entity.StateMachineStateCurrent;
import com.richie.component.statemachine.persistence.dao.mapper.StateMachineStateCurrentMapper;
import com.richie.component.statemachine.storage.StateMachineKeyBuilder;
import com.richie.component.statemachine.storage.StateStorage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 状态机终态清理定时任务
 * <p>
 * 用于自动清理 Redis 中“长时间处于终态/错误态”的当前状态数据，防止 Redis 中的永久状态数据无限增长。
 * <p>
 * 设计原则：
 * <ul>
 *   <li>以数据库 statemachine_state_current 作为“索引”，按批次查询候选记录</li>
 *   <li>仅清理处于 FINAL/ERROR 状态、且 updated_at 早于配置阈值的记录</li>
 *   <li>通过 {@link StateMachineKeyBuilder} 计算 Redis Key，调用 {@link GlobalCache#removeCache(String)} 删除</li>
 *   <li>不改变数据库中的记录，数据库仍作为审计/归档数据源</li>
 * </ul>
 *
 * <p>
 * 启用条件：
 * <ul>
 *   <li>{@code platform.component.statemachine.storage.cleanup.enabled = true}</li>
 *   <li>{@code platform.component.statemachine.storage.db.enabled = true}（数据库复制已开启）</li>
 * </ul>
 *
 * <p>
 * 配置示例：
 * <pre>{@code
 * platform:
 *   component:
 *     statemachine:
 *       storage:
 *         cleanup:
 *           enabled: true
 *           ttl-days: 30
 *           batch-size: 500
 * }</pre>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "platform.component.statemachine", name = "storage-type", havingValue = "REDIS", matchIfMissing = true)
@ConditionalOnProperty(prefix = "platform.component.statemachine.redis-storage.cleanup", name = "enabled", havingValue = "true")
public class StateCleanupScheduler {

    private final StateMachineProperties properties;

    private final StateMachineDefinitionRegistry definitionRegistry;

    private final StateMachineStateCurrentMapper currentStateMapper;

    /**
     * 状态存储接口
     * <p>
     * 通过 {@link StateStorage#deleteState(String, Long)} 统一删除当前状态和历史列表，
     * 避免在此类中散落对 Redis Key 的拼接逻辑。
     * 
     */
    private final StateStorage stateStorage;

    /**
     * 清理终态/错误态状态机当前状态的定时任务
     * <p>
     * 使用 {@link RedisStreamConfig.StorageCleanupConfig} 中的配置控制阈值和批次大小。
     * 定时表达式使用 Spring 的 {@code cleanIntervalMs}，默认 1 小时执行一次。
     */
    @Scheduled(fixedDelayString = "${platform.component.statemachine.redis-storage.cleanup.fixed-delay-ms:3600000}")
    public void cleanupFinalStates() {
        // 未开启数据库复制时不执行清理任务（因为依赖 DB 记录作为索引）
        RedisStreamConfig.StorageCleanupConfig cleanupConfig = properties.getRedisStream().getCleanup();
        if (cleanupConfig == null || !cleanupConfig.isEnabled()) {
            return;
        }

        long ttlDays = cleanupConfig.getTtlDays();
        if (ttlDays <= 0) {
            // 非法配置，直接跳过
            log.debug("状态机终态清理任务已跳过，原因：ttlDays 配置无效（{}）", ttlDays);
            return;
        }

        int batchSize = cleanupConfig.getBatchSize();
        if (batchSize <= 0) {
            batchSize = 500;
        }

        // 全局分布式锁，避免多实例重复执行清理逻辑
        String lockKey = properties.getRedisStream().getKeyPrefix() + ":cleanup:final-state-lock";
        // 锁过期时间：至少大于一次清理可能耗时，这里默认 60 秒，支持自动续期
        try (CacheLock lock = GlobalCache.optimisticLockWithRenewal(lockKey, 60L)) {
            if (lock == null || !lock.isSuccess()) {
                // 获取不到锁，说明已有其他实例在执行清理任务，当前实例直接跳过
                log.debug("状态机终态清理任务跳过，本实例未获取到分布式锁: {}", lockKey);
                return;
            }

            // 计算阈值时间：updated_at 早于此时间的记录才有资格被清理
            LocalDateTime thresholdTime = LocalDateTime.ofInstant(
                    Instant.now().minusSeconds(ttlDays * 24L * 60L * 60L),
                    ZoneId.systemDefault()
            );

            log.debug("开始执行状态机终态清理任务，ttlDays={}，thresholdTime={}", ttlDays, thresholdTime);

            // 一次只处理一个批次，避免长时间占用数据库和 Redis
            List<StateMachineStateCurrent> candidates = currentStateMapper.selectList(
                    new LambdaQueryWrapper<StateMachineStateCurrent>()
                            .lt(StateMachineStateCurrent::getUpdatedAt, thresholdTime)
                            .last("LIMIT %d".formatted(batchSize))
            );

            if (CollectionUtils.isEmpty(candidates)) {
                log.debug("状态机终态清理任务结束：没有需要清理的候选记录");
                return;
            }

            // 预先构建所有状态机的“终态/错误态状态名集合”，避免重复计算
            // key: stateMachineName, value: set of final/error state names
            Map<String, Set<String>> finalStateCache = new HashMap<>();
            int removedCount = 0;

            for (StateMachineStateCurrent record : candidates) {
                String stateMachineName = record.getStateMachine();
                Long businessId = record.getBusinessId();
                String currentState = record.getCurrentState();

                if (stateMachineName == null || businessId == null || currentState == null) {
                    continue;
                }

                // 获取或构建该状态机的 FINAL/ERROR 状态集合
                Set<String> finalLikeStates = finalStateCache.computeIfAbsent(stateMachineName, name -> {
                    StateMachineDefinition definition = getDefinitionSafe(name);
                    if (definition == null || definition.getStates() == null) {
                        return Collections.emptySet();
                    }
                    return definition.getStates().stream()
                            .filter(s -> "FINAL".equalsIgnoreCase(s.getType()) || "ERROR".equalsIgnoreCase(s.getType()))
                            .map(StateMachineDefinition.StateDefinition::getName)
                            .collect(Collectors.toSet());
                });

                if (!finalLikeStates.contains(currentState)) {
                    continue;
                }

                // 使用 StateStorage 统一删除状态（当前状态 + 历史列表）
                try {
                    stateStorage.deleteState(stateMachineName, businessId);
                    removedCount++;
                } catch (Exception e) {
                    log.warn("删除状态机 Redis 状态数据失败: stateMachine={}, businessId={}", stateMachineName, businessId, e);
                }
            }

            if (removedCount > 0) {
                log.info("状态机终态清理任务完成，本次共删除 Redis 状态数据 {} 条", removedCount);
            } else {
                log.debug("状态机终态清理任务完成，本次没有符合条件的终态记录");
            }
        }
    }

    /**
     * 安全获取状态机定义
     *
     * @param stateMachineName 状态机名称
     * @return 状态机定义，如果不存在则返回 null
     */
    private StateMachineDefinition getDefinitionSafe(String stateMachineName) {
        if (stateMachineName == null) {
            return null;
        }
        try {
            return definitionRegistry.getDefinition(stateMachineName);
        } catch (Exception e) {
            log.warn("获取状态机定义失败: {}", stateMachineName, e);
            return null;
        }
    }
}

