package com.richie.component.statemachine.storage.impl;

import com.richie.component.cache.GlobalCache;
import com.richie.context.utils.data.JsonUtils;
import com.richie.component.statemachine.config.StateMachineProperties;
import com.richie.component.statemachine.context.StateContext;
import com.richie.component.statemachine.persistence.dao.entity.StateMachineStateCurrent;
import com.richie.component.statemachine.persistence.dao.mapper.StateMachineStateCurrentMapper;
import com.richie.component.statemachine.storage.StateHistory;
import com.richie.component.statemachine.storage.StateMachineKeyBuilder;
import com.richie.component.statemachine.storage.StateStorage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis 状态存储实现
 * <p>
 * 使用 Redis 作为状态存储，适用于微服务多实例部署场景，支持状态共享。
 * 实现缓存预热机制：当 Redis 中没有数据时，自动从数据库加载并回填到 Redis。
 *
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisStateStorage implements StateStorage {

    private static final TypeReference<List<StateHistory>> HISTORY_LIST_TYPE = new TypeReference<>() {
    };

    /**
     * 状态机配置属性
     */
    private final StateMachineProperties properties;

    /**
     * Redis Key 构建器
     */
    private final StateMachineKeyBuilder keyBuilder;

    /**
     * 当前状态 Mapper（可选，仅在数据库持久化启用时存在）
     */
    @Autowired(required = false)
    private StateMachineStateCurrentMapper currentStateMapper;

    /**
     * 获取历史记录过期时间（单位：毫秒）
     * <p>
     * 历史记录必须有过期时间，避免 Redis 数据量过大。
     * 默认 7 天，如果配置为 0 则强制使用 7 天。
     *
     *
     * @return 历史记录过期时间（毫秒）
     */
    private long getHistoryTimeout() {
        long timeout = properties.getRedisStream().getTimeout();
        // 历史记录必须有过期时间，如果配置为0则使用默认7天
        if (timeout <= 0) {
            return TimeUnit.DAYS.toMillis(7); // 默认7天
        }
        return timeout;
    }

    /**
     * 保存当前状态
     * <p>
     * 将当前状态保存到 Redis，永不过期（因为状态机状态需要持久化）。
     *
     *
     * @param stateMachineName 状态机名称
     * @param businessId       业务对象ID
     * @param currentState     当前状态
     * @param context          状态上下文
     */
    @Override
    public void saveCurrentState(String stateMachineName, Long businessId, String currentState, StateContext context) {
        String key = keyBuilder.buildCurrentStateKey(stateMachineName, businessId);

        // 存储当前状态（永不过期，因为状态机状态需要持久化）
        GlobalCache.value().set(key, currentState);

        log.debug("保存当前状态到Redis: {} -> {}", key, currentState);
    }

    /**
     * 获取当前状态
     * <p>
     * 从 Redis 获取当前状态。如果数据库持久化已启用，会使用防缓存击穿机制：
     * 当 Redis 中没有数据时，自动从数据库加载并回填到 Redis。
     *
     *
     * @param stateMachineName 状态机名称
     * @param businessId       业务对象ID
     * @return 当前状态，如果不存在则返回 null
     */
    @Override
    public String getCurrentState(String stateMachineName, Long businessId) {
        String key = keyBuilder.buildCurrentStateKey(stateMachineName, businessId);

        // 使用防缓存击穿方法：Redis -> DB -> null
        // 如果数据库持久化未启用，则直接从Redis读取
        if (!properties.getRedisStream().getDbReplication().isEnabled()) {
            String currentState = GlobalCache.value().get(key, String.class);
            log.debug("从Redis获取当前状态: {} -> {}", key, currentState);
            return currentState;
        }

        // 如果数据库持久化已启用，使用防缓存击穿机制
        // 当Redis中没有数据时，自动从数据库加载并回填到Redis
        if (currentStateMapper == null) {
            // 如果 Mapper 不存在（数据库持久化未启用），直接从 Redis 读取
            String currentState = GlobalCache.value().get(key, String.class);
            log.debug("从Redis获取当前状态: {} -> {}", key, currentState);
            return currentState;
        }

        // 当前状态使用配置的过期时间（用于缓存预热），如果为0则使用7天
        long timeout = properties.getRedisStream().getTimeout();
        if (timeout <= 0) {
            timeout = TimeUnit.DAYS.toMillis(7);
        }
        String currentState = GlobalCache.value().getWithLock(key, timeout, () -> {
            // 数据库加载器：从数据库查询当前状态
            try {
                LambdaQueryWrapper<StateMachineStateCurrent> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(StateMachineStateCurrent::getStateMachine, stateMachineName)
                        .eq(StateMachineStateCurrent::getBusinessId, businessId);

                StateMachineStateCurrent current = currentStateMapper.selectOne(wrapper);
                if (current != null && current.getCurrentState() != null) {
                    log.debug("从数据库加载状态到Redis: stateMachine={}, businessId={}, state={}",
                            stateMachineName, businessId, current.getCurrentState());
                    return current.getCurrentState();
                }
                log.debug("数据库中未找到状态: stateMachine={}, businessId={}", stateMachineName, businessId);
                return null; // 数据库也没有，返回null，由上层返回初始状态
            } catch (Exception e) {
                log.warn("从数据库加载状态失败: stateMachine={}, businessId={}", stateMachineName, businessId, e);
                return null; // 异常时返回null，避免影响主流程
            }
        });

        log.debug("获取当前状态: {} -> {}", key, currentState);
        return currentState;
    }

    /**
     * 保存状态历史
     * <p>
     * 将状态变更历史保存到 Redis，包括：
     * 1. 单个历史记录对象（Hash 结构，用于快速查询）
     * 2. 历史记录列表（List 结构，用于批量查询）
     * 历史记录会设置过期时间，避免 Redis 数据量过大。
     *
     *
     * @param stateMachineName 状态机名称
     * @param businessId       业务对象ID
     * @param fromState        源状态
     * @param toState          目标状态
     * @param event            触发事件
     * @param context          状态上下文
     */
    @Override
    public void saveStateHistory(String stateMachineName, Long businessId, String fromState, String toState, String event, StateContext context) {
        // 创建历史记录对象
        StateHistory history = new StateHistory(stateMachineName, businessId, fromState, toState, event);
        history.setOperator((String) context.getAttribute("operator"));
        history.setRemark((String) context.getAttribute("remark"));
        history.setAttributes(context.getAttributes());
        Object seqValue = context.getAttribute("seq");
        if (seqValue instanceof Number number) {
            history.setSeq(number.longValue());
        } else {
            history.setSeq(0L);
        }

        // 历史记录必须有过期时间，避免Redis数据量过大
        long historyTimeout = getHistoryTimeout();
        String listKey = keyBuilder.buildHistoryListKey(stateMachineName, businessId);

        List<StateHistory> histories = readHistoryList(listKey);
        history.setCreateTime(null);
        histories.forEach(item -> item.setCreateTime(null));
        histories.add(history);
        GlobalCache.value().set(listKey, JsonUtils.getInstance().serialize(histories), historyTimeout);

        log.debug("保存状态历史到Redis（过期时间: {}天）: {} -> {} -> {}",
                historyTimeout / (24L * 60 * 60 * 1000), fromState, toState, event);
    }

    /**
     * 获取状态历史
     * <p>
     * 从 Redis 获取指定业务对象的状态变更历史记录列表。
     * 返回的记录按创建时间降序排序（最新的在前）。
     *
     *
     * @param stateMachineName 状态机名称
     * @param businessId       业务对象ID
     * @return 状态历史记录列表，按创建时间降序排序
     */
    @Override
    public List<StateHistory> getStateHistory(String stateMachineName, Long businessId) {
        String listKey = keyBuilder.buildHistoryListKey(stateMachineName, businessId);
        List<StateHistory> histories = new ArrayList<>(readHistoryList(listKey));

        if (histories.isEmpty()) {
            return histories;
        }

        // 追加写入为时间正序，对外返回最新在前
        Collections.reverse(histories);
        return histories;
    }

    private List<StateHistory> readHistoryList(String listKey) {
        String json = GlobalCache.value().get(listKey, String.class);
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        List<StateHistory> histories = JsonUtils.getInstance().deserialize(json, HISTORY_LIST_TYPE);
        if (histories == null) {
            return new ArrayList<>();
        }
        histories.forEach(item -> item.setCreateTime(null));
        return histories;
    }

    /**
     * 删除状态数据
     * <p>
     * 删除指定业务对象的状态数据，包括当前状态和历史记录列表。
     * 注意：历史记录 Hash 不会自动删除（因为 key 包含时间戳），可以通过定期清理任务处理。
     *
     *
     * @param stateMachineName 状态机名称
     * @param businessId       业务对象ID
     */
    @Override
    public void deleteState(String stateMachineName, Long businessId) {
        // 删除当前状态
        String currentStateKey = keyBuilder.buildCurrentStateKey(stateMachineName, businessId);
        GlobalCache.key().removeCache(currentStateKey);

        // 删除历史记录列表
        String historyListKey = keyBuilder.buildHistoryListKey(stateMachineName, businessId);
        GlobalCache.key().removeCache(historyListKey);

        // 注意：历史记录Hash不会自动删除，因为key包含时间戳，可以通过定期清理任务处理
        // 或者根据业务需求设置合适的过期时间

        log.debug("从Redis删除状态数据: stateMachine={}, businessId={}", stateMachineName, businessId);
    }
}

