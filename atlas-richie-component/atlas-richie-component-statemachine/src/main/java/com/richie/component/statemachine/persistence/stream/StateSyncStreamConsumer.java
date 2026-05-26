package com.richie.component.statemachine.persistence.stream;

import com.richie.component.cache.redis.stream.AbstractStreamConsumer;
import com.richie.component.cache.redis.stream.EventContext;
import com.richie.component.cache.redis.stream.RedisStreamConsumer;
import com.richie.component.statemachine.config.StateMachineProperties;
import com.richie.component.statemachine.event.StateSyncKey;
import com.richie.component.statemachine.event.StateSyncMessage;
import com.richie.component.statemachine.persistence.dao.entity.StateMachineStateCurrent;
import com.richie.component.statemachine.persistence.dao.entity.StateMachineStateHistory;
import com.richie.component.statemachine.persistence.dao.mapper.StateMachineStateCurrentMapper;
import com.richie.component.statemachine.persistence.dao.mapper.StateMachineStateHistoryMapper;
import com.richie.component.statemachine.storage.StateHistory;
import com.richie.component.statemachine.storage.StateStorage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 状态同步 Redis Stream 消费者
 * <p>
 * 负责从 Redis Stream 消费状态同步消息，从 Redis 读取最新状态并持久化到数据库。
 * <p>
 * 核心原则：
 * <ol>
 *   <li><b>Redis 作为 Single Source of Truth</b>：状态数据始终从 Redis 读取，确保数据库与 Redis 一致</li>
 *   <li><b>消息仅作为触发器</b>：消息只包含同步键（stateMachineName:businessId），不包含状态数据</li>
 *   <li><b>从 Redis 读取最新状态</b>：消费者从 Redis（通过 StateStorage）读取最新状态和历史</li>
 *   <li><b>批量处理优化</b>：支持批量写入数据库，提升性能</li>
 * </ol>
 *
 * @author richie696
 * @since 5.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "platform.component.statemachine", name = "storage-type", havingValue = "REDIS", matchIfMissing = true)
@ConditionalOnProperty(prefix = "platform.component.statemachine.redis-storage.redis-stream", name = "enabled", havingValue = "true")
@RedisStreamConsumer("state-sync")
@MapperScan("com.richie.component.statemachine.persistence.dao.mapper")
public class StateSyncStreamConsumer extends AbstractStreamConsumer<StateSyncMessage> {

    private final StateStorage stateStorage;
    private final StateMachineStateCurrentMapper currentStateMapper;
    private final StateMachineStateHistoryMapper historyMapper;
    private final int batchSize;


    // 线程本地缓冲区，用于批量收集消息
    private final ThreadLocal<List<MessageWithContext>> batchBuffer = ThreadLocal.withInitial(ArrayList::new);
    private final ThreadLocal<AtomicInteger> batchCounter = ThreadLocal.withInitial(() -> new AtomicInteger(0));

    /**
     * 构造函数
     *
     * @param properties         状态机配置属性
     * @param stateStorage       状态存储接口
     * @param currentStateMapper 当前状态 Mapper
     * @param historyMapper      历史记录 Mapper
     */
    public StateSyncStreamConsumer(StateMachineProperties properties,
                                   StateStorage stateStorage,
                                   StateMachineStateCurrentMapper currentStateMapper,
                                   StateMachineStateHistoryMapper historyMapper) {
        this.stateStorage = stateStorage;
        this.currentStateMapper = currentStateMapper;
        this.historyMapper = historyMapper;
        this.batchSize = properties.getRedisStream().getDbReplication().getBatchSize();
    }

    /**
     * 消息和上下文的包装类
     * <p>
     * 用于在批量处理时同时保存消息和事件上下文。
     *
     */
    private static class MessageWithContext {
        /**
         * 状态同步消息
         */
        final StateSyncMessage message;

        /**
         * 事件上下文
         */
        final EventContext ctx;

        /**
         * 构造函数
         *
         * @param message 状态同步消息
         * @param ctx     事件上下文
         */
        MessageWithContext(StateSyncMessage message, EventContext ctx) {
            this.message = message;
            this.ctx = ctx;
        }
    }

    /**
     * 处理消息
     * <p>
     * 将消息添加到线程本地缓冲区，当达到批量大小时自动刷写到数据库。
     *
     * @param message 状态同步消息
     * @param ctx     事件上下文
     * @throws Exception 处理异常
     */
    @Override
    protected void handle(StateSyncMessage message, EventContext ctx) throws Exception {

        // 将消息添加到缓冲区
        List<MessageWithContext> buffer = batchBuffer.get();
        buffer.add(new MessageWithContext(message, ctx));

        AtomicInteger counter = batchCounter.get();
        int currentCount = counter.incrementAndGet();

        // 当达到批量大小时，执行批量保存
        if (currentCount >= batchSize) {
            flushBatch();
        }
    }

    /**
     * 错误处理
     * <p>
     * 当消息处理失败时调用，记录错误日志。
     * 错误处理策略由框架根据配置决定（如 SKIP、RETRY 等）。
     *
     * @param e       异常对象
     * @param payload 消息负载
     * @param ctx     事件上下文
     */
    @Override
    protected void onError(Throwable e, StateSyncMessage payload, EventContext ctx) {
        log.error("状态同步消息处理异常: syncKey={}, error={}",
                payload != null ? payload.syncKey() : "unknown", e.getMessage(), e);

        // 错误处理：可以选择发送到死信队列或记录告警
        // 这里只记录日志，由框架根据错误策略决定是否 ACK
    }

    /**
     * 服务关闭时刷写剩余的缓冲区
     */
    @PreDestroy
    public void flushRemainingMessages() {
        try {
            log.info("开始刷写剩余的状态同步消息");

            // 刷写所有线程的缓冲区
            List<MessageWithContext> buffer = batchBuffer.get();
            if (!buffer.isEmpty()) {
                log.info("发现 {} 条未处理的消息，开始刷写", buffer.size());
                flushBatch();
            }

            // 清理 ThreadLocal，避免内存泄漏
            batchBuffer.remove();
            batchCounter.remove();

            log.info("剩余消息刷写完成");
        } catch (Exception e) {
            log.error("刷写剩余消息失败", e);
        }
    }

    /**
     * 批量刷写到数据库
     * <p>
     * 从线程本地缓冲区读取消息，从 Redis 读取最新状态和历史记录，
     * 然后批量写入数据库。写入完成后确认所有消息。
     *
     */
    private void flushBatch() {
        List<MessageWithContext> buffer = batchBuffer.get();
        if (buffer.isEmpty()) {
            return;
        }

        try {
            // 批量处理消息
            List<StateSyncKey> syncKeys = new ArrayList<>();
            for (MessageWithContext item : buffer) {
                StateSyncKey syncKey = item.message.parse();
                if (syncKey != null) {
                    syncKeys.add(syncKey);
                }
            }

            if (syncKeys.isEmpty()) {
                // 清空缓冲区并确认所有消息
                buffer.forEach(item -> {
                    if (item.ctx != null) {
                        item.ctx.ack();
                    }
                });
                buffer.clear();
                batchCounter.get().set(0);
                return;
            }

            // 批量读取状态并准备批量写入数据
            List<StateMachineStateCurrent> currentStateBatch = new ArrayList<>();
            List<StateMachineStateHistory> historyBatch = new ArrayList<>();

            for (StateSyncKey syncKey : syncKeys) {
                String stateMachineName = syncKey.stateMachineName();
                Long businessId = syncKey.businessId();

                // 从 Redis 读取历史记录（即使 currentState 为 null，也可能有历史记录需要同步）
                List<StateHistory> histories = stateStorage.getStateHistory(stateMachineName, businessId);
                // 计算本次同步的业务时间：优先取最新一条历史的 occurredAt，若无历史则为 now()
                LocalDateTime businessTime = null;
                if (histories != null && !histories.isEmpty()) {
                    // 只写入最新的几条历史记录，避免重复写入
                    int maxHistoryToSync = Math.min(batchSize, histories.size());
                    for (int i = 0; i < maxHistoryToSync; i++) {
                        StateHistory history = histories.get(i);
                        if (history.getCreateTime() != null) {
                            historyBatch.add(StateMachineStateHistory.builder()
                                    .stateMachine(history.getStateMachineName())
                                    .businessId(history.getBusinessId())
                                    .prevState(history.getFromState())
                                    .currState(history.getToState())
                                    .eventName(history.getEvent())
                                    .seq(history.getSeq())
                                    .occurredAt(history.getCreateTime())
                                    .build());
                            if (businessTime == null || history.getCreateTime().isAfter(businessTime)) {
                                businessTime = history.getCreateTime();
                            }
                        }
                    }
                }

                // 从 Redis（通过 StateStorage）读取最新状态，并写入当前状态
                String currentState = stateStorage.getCurrentState(stateMachineName, businessId);
                if (currentState != null) {
                    java.time.LocalDateTime updatedAt = businessTime != null ? businessTime : java.time.LocalDateTime.now();
                    currentStateBatch.add(StateMachineStateCurrent.builder()
                            .stateMachine(stateMachineName)
                            .businessId(businessId)
                            .currentState(currentState)
                            .seq(resolveLatestSeq(histories))
                            .updatedAt(updatedAt)
                            .build());
                }
            }

            // 批量写入当前状态（使用 MyBatis Plus）
            if (!currentStateBatch.isEmpty()) {
                // 批量查询已存在的记录（优化：减少数据库查询次数）
                List<String> stateMachineNames = currentStateBatch.stream()
                        .map(StateMachineStateCurrent::getStateMachine)
                        .distinct()
                        .toList();
                List<Long> businessIds = currentStateBatch.stream()
                        .map(StateMachineStateCurrent::getBusinessId)
                        .distinct()
                        .toList();

                // 批量查询已存在的记录
                List<StateMachineStateCurrent> existingList = currentStateMapper.selectList(
                        new LambdaQueryWrapper<StateMachineStateCurrent>()
                                .in(StateMachineStateCurrent::getStateMachine, stateMachineNames)
                                .in(StateMachineStateCurrent::getBusinessId, businessIds)
                );

                // 构建已存在记录的键集合（stateMachine:businessId）
                java.util.Set<String> existingKeys = existingList.stream()
                        .map(e -> e.getStateMachine() + ":" + e.getBusinessId())
                        .collect(java.util.stream.Collectors.toSet());

                // 分离需要插入和更新的记录
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

                // 批量更新（MyBatis Plus 的 updateById 需要主键，但这里是复合主键，需要特殊处理）
                // 由于是复合主键，需要逐个更新
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

            // 批量写入历史记录（使用 MyBatis Plus 的批量插入，通过唯一约束去重）
            if (!historyBatch.isEmpty()) {
                // 批量查询已存在的记录（优化：减少数据库查询次数）
                List<String> stateMachineNames = historyBatch.stream()
                        .map(StateMachineStateHistory::getStateMachine)
                        .distinct()
                        .toList();
                List<Long> businessIds = historyBatch.stream()
                        .map(StateMachineStateHistory::getBusinessId)
                        .distinct()
                        .toList();
                // 批量查询已存在的记录
                List<StateMachineStateHistory> existingList = historyMapper.selectList(
                        new LambdaQueryWrapper<StateMachineStateHistory>()
                                .in(StateMachineStateHistory::getStateMachine, stateMachineNames)
                                .in(StateMachineStateHistory::getBusinessId, businessIds)
                );

                // 构建已存在记录的键集合（stateMachine:businessId:occurredAt）
                java.util.Set<String> existingKeys = existingList.stream()
                        .map(e -> e.getStateMachine() + ":" + e.getBusinessId() + ":" + (e.getSeq() != null ? e.getSeq() : e.getOccurredAt()))
                        .collect(java.util.stream.Collectors.toSet());

                // 过滤出不存在的记录，批量插入
                List<StateMachineStateHistory> toInsert = historyBatch.stream()
                        .filter(h -> {
                            String key = h.getStateMachine() + ":" + h.getBusinessId() + ":" + (h.getSeq() != null ? h.getSeq() : h.getOccurredAt());
                            return !existingKeys.contains(key);
                        })
                        .toList();

                // 批量插入（MyBatis Plus 的批量插入）
                if (!toInsert.isEmpty()) {
                    for (StateMachineStateHistory history : toInsert) {
                        historyMapper.insert(history);
                    }
                }
            }

            // 确认所有消息
            buffer.forEach(item -> {
                if (item.ctx != null) {
                    item.ctx.ack();
                }
            });

            log.debug("批量状态同步完成: 当前状态 {} 条, 历史记录 {} 条",
                    currentStateBatch.size(), historyBatch.size());

        } catch (Exception e) {
            log.error("批量状态同步失败", e);
            throw e;
        } finally {
            // 清空缓冲区
            buffer.clear();
            batchCounter.get().set(0);
        }
    }

    private Long resolveLatestSeq(List<StateHistory> histories) {
        if (histories == null || histories.isEmpty()) {
            return 0L;
        }
        return histories.stream()
                .map(StateHistory::getSeq)
                .filter(java.util.Objects::nonNull)
                .max(Long::compareTo)
                .orElse(0L);
    }

}

