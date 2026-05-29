package com.richie.component.statemachine.persistence;

import com.richie.component.cache.GlobalCache;
import com.richie.component.statemachine.config.StateMachineProperties;
import com.richie.component.statemachine.context.StateContext;
import com.richie.component.statemachine.exception.StateMachineException;
import com.richie.component.statemachine.persistence.dao.entity.StateMachineStateCurrent;
import com.richie.component.statemachine.persistence.dao.entity.StateMachineStateHistory;
import com.richie.component.statemachine.persistence.dao.mapper.StateMachineStateCurrentMapper;
import com.richie.component.statemachine.persistence.dao.mapper.StateMachineStateHistoryMapper;
import com.richie.component.statemachine.storage.StateMachineKeyBuilder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 状态机数据库持久化服务
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StateDbPersistenceService {

    private final StateMachineKeyBuilder keyBuilder;
    private final StateMachineProperties properties;

    @Autowired(required = false)
    private StateMachineStateCurrentMapper currentStateMapper;

    @Autowired(required = false)
    private StateMachineStateHistoryMapper historyMapper;

    public long nextSeq(String stateMachineName, Long businessId) {
        String seqKey = keyBuilder.buildSeqKey(stateMachineName, businessId);
        return GlobalCache.increment(seqKey, TimeUnit.DAYS.toMillis(30));
    }

    public void persistSync(String stateMachineName, Long businessId, String eventName, StateContext context) {
        if (!isDbPersistenceAvailable()) {
            throw new StateMachineException("STATE_DB_UNAVAILABLE",
                    "数据库持久化不可用：Mapper 未注入");
        }
        int maxRetry = Math.max(0, properties.getSyncRetryTimes());
        long retryIntervalMs = Math.max(0L, properties.getSyncRetryIntervalMs());
        RuntimeException lastException = null;
        for (int attempt = 0; attempt <= maxRetry; attempt++) {
            try {
                persistCurrentWithSeqGuard(stateMachineName, businessId, context);
                if (properties.isEnableHistory()) {
                    persistHistoryIfAbsent(stateMachineName, businessId, eventName, context);
                }
                return;
            } catch (RuntimeException e) {
                lastException = e;
                if (attempt >= maxRetry) {
                    break;
                }
                sleepQuietly(retryIntervalMs);
            }
        }
        if (lastException instanceof StateMachineException stateMachineException) {
            throw stateMachineException;
        }
        throw new StateMachineException("STATE_DB_SYNC_FAILED",
                "同步持久化失败: stateMachine=%s, businessId=%s".formatted(stateMachineName, businessId),
                lastException);
    }

    public boolean isDbPersistenceAvailable() {
        return currentStateMapper != null && historyMapper != null;
    }

    private void persistCurrentWithSeqGuard(String stateMachineName, Long businessId, StateContext context) {
        Long incomingSeq = readSeq(context);
        StateMachineStateCurrent current = currentStateMapper.selectOne(
                new LambdaQueryWrapper<StateMachineStateCurrent>()
                        .eq(StateMachineStateCurrent::getStateMachine, stateMachineName)
                        .eq(StateMachineStateCurrent::getBusinessId, businessId)
        );
        if (current != null && incomingSeq <= current.getSeq()) {
            log.info("忽略过期/重复当前状态写入: stateMachine={}, businessId={}, incomingSeq={}, persistedSeq={}",
                    stateMachineName, businessId, incomingSeq, current.getSeq());
            return;
        }

        StateMachineStateCurrent toSave = StateMachineStateCurrent.builder()
                .stateMachine(stateMachineName)
                .businessId(businessId)
                .currentState(context.getCurrentState())
                .seq(incomingSeq)
                .updatedAt(context.getUpdateTime() != null ? context.getUpdateTime() : LocalDateTime.now())
                .build();

        if (current == null) {
            currentStateMapper.insert(toSave);
            return;
        }
        currentStateMapper.update(toSave, new LambdaQueryWrapper<StateMachineStateCurrent>()
                .eq(StateMachineStateCurrent::getStateMachine, stateMachineName)
                .eq(StateMachineStateCurrent::getBusinessId, businessId));
    }

    private void persistHistoryIfAbsent(String stateMachineName, Long businessId, String eventName, StateContext context) {
        LocalDateTime occurredAt = context.getUpdateTime() != null ? context.getUpdateTime() : LocalDateTime.now();
        Long incomingSeq = readSeq(context);

        List<StateMachineStateHistory> existing = historyMapper.selectList(
                new LambdaQueryWrapper<StateMachineStateHistory>()
                        .eq(StateMachineStateHistory::getStateMachine, stateMachineName)
                        .eq(StateMachineStateHistory::getBusinessId, businessId)
                        .eq(StateMachineStateHistory::getSeq, incomingSeq)
                        .eq(StateMachineStateHistory::getOccurredAt, occurredAt)
        );
        if (!existing.isEmpty()) {
            return;
        }

        StateMachineStateHistory history = StateMachineStateHistory.builder()
                .stateMachine(stateMachineName)
                .businessId(businessId)
                .prevState(context.getPreviousState())
                .currState(context.getCurrentState())
                .eventName(eventName)
                .seq(incomingSeq)
                .occurredAt(occurredAt)
                .build();
        historyMapper.insert(history);
    }

    private Long readSeq(StateContext context) {
        Object seq = context.getAttribute("seq");
        if (seq instanceof Number n) {
            return n.longValue();
        }
        return 0L;
    }

    private void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

