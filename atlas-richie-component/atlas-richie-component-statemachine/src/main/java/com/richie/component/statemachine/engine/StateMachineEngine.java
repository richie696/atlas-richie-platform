package com.richie.component.statemachine.engine;

import com.richie.component.redis.streammq.StreamMQ;
import com.richie.component.statemachine.StateMachineEvent;
import com.richie.component.statemachine.StateMachineName;
import com.richie.component.statemachine.config.StateMachineDefinition;
import com.richie.component.statemachine.config.StateMachineDefinitionRegistry;
import com.richie.component.statemachine.config.StateMachineProperties;
import com.richie.component.statemachine.config.StatePersistenceModeResolver;
import com.richie.component.statemachine.config.properties.DbPersistenceMode;
import com.richie.component.statemachine.context.StateContext;
import com.richie.component.statemachine.event.StateChangedEvent;
import com.richie.component.statemachine.event.StateSyncMessage;
import com.richie.component.statemachine.exception.StateMachineException;
import com.richie.component.statemachine.model.State;
import com.richie.component.statemachine.model.StateMachineModel;
import com.richie.component.statemachine.model.Transition;
import com.richie.component.statemachine.config.properties.StorageType;
import com.richie.component.statemachine.persistence.StateDbPersistenceService;
import com.richie.component.statemachine.persistence.async.AsyncThreadStorageManager;
import com.richie.component.statemachine.registry.StateMachineRegistry;
import com.richie.component.statemachine.rule.ExpressionConfigHolder;
import com.richie.component.statemachine.rule.StateTransitionRule;
import com.richie.component.statemachine.storage.StateHistory;
import com.richie.component.statemachine.storage.StateMachineKeyBuilder;
import com.richie.component.statemachine.storage.StateStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeasy.rules.api.Facts;
import org.jeasy.rules.api.Rules;
import org.jeasy.rules.api.RulesEngine;
import org.jeasy.rules.api.RulesEngineParameters;
import org.jeasy.rules.core.DefaultRulesEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 状态机引擎
 * <p>
 * 核心组件，负责执行状态转换逻辑。支持基于 Easy Rules 的规则引擎、MVEL 表达式评估、
 * 状态转换校验、历史记录、事件发布等功能。
 *
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StateMachineEngine {

    /**
     * 状态机注册表，用于获取状态机模型定义
     */
    private final StateMachineRegistry stateMachineRegistry;

    /**
     * 状态机配置属性
     */
    private final StateMachineProperties properties;

    /**
     * 状态存储接口，用于持久化当前状态和历史记录
     */
    private final StateStorage stateStorage;

    /**
     * Spring 事件发布器，用于发布状态变更事件
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Redis Key 构建器，用于生成 Redis Stream 键
     */
    private final StateMachineKeyBuilder keyBuilder;

    /**
     * 状态机定义注册表，用于获取状态机配置定义
     */
    private final StateMachineDefinitionRegistry definitionRegistry;

    /**
     * 异步线程池数据库复制服务（可选，仅在 ASYNC_THREAD 模式时注入）
     */
    @Autowired(required = false)
    private AsyncThreadStorageManager asyncThreadStorageManager;

    @Autowired(required = false)
    private StateDbPersistenceService stateDbPersistenceService;

    // ================== 基于 businessId 的无对象 API（Long 类型） ==================

    /**
     * 触发状态转换（基础版）
     * <p>
     * 使用场景：仅需触发事件，不额外携带上下文，也不显式传入当前状态。
     * 说明：当前状态将从存储中读取，若不存在则使用状态机初始状态。
     *
     *
     * @param stateMachineName 状态机名称（枚举）
     * @param event            触发事件（枚举）
     * @param businessId       业务唯一标识（Long 类型，支持与业务表的数值型主键直接关联查询）
     * @return 状态转换结果（成功/失败、上下文等）
     */
    public StateTransitionResult fire(Enum<?> stateMachineName, Enum<?> event, Long businessId) {
        return fire(StateMachineName.of(stateMachineName), StateMachineEvent.of(event), businessId, null, null);
    }

    /**
     * 触发状态转换（带上下文属性）
     * <p>
     * 使用场景：规则条件/动作需要额外的上下文数据（如操作者、原因、请求参数等）。
     * 说明：上下文属性将注入到 StateContext，可被条件/动作表达式访问。
     *
     *
     * @param stateMachineName 状态机名称（枚举）
     * @param event            触发事件（枚举）
     * @param businessId       业务唯一标识（Long 类型，支持与业务表的数值型主键直接关联查询）
     * @param attributes       额外上下文（可为 null）
     * @return 状态转换结果
     */
    public StateTransitionResult fire(Enum<?> stateMachineName, Enum<?> event, Long businessId, Map<String, Object> attributes) {
        return fire(StateMachineName.of(stateMachineName), StateMachineEvent.of(event), businessId, attributes, null);
    }

    /**
     * 触发状态转换（带上下文属性与枚举型当前状态）
     * <p>
     * 使用场景：同时需要上下文数据与显式当前状态的高性能路径（避免一次存储读取）。
     * 说明：适用于对延迟敏感、且业务侧已维护当前状态的场景。
     *
     *
     * @param stateMachineName 状态机名称（枚举）
     * @param event            触发事件（枚举）
     * @param businessId       业务唯一标识（Long 类型，支持与业务表的数值型主键直接关联查询）
     * @param attributes       额外上下文（可为 null）
     * @param currentStateEnum 业务当前状态（枚举，传 null 表示从存储读取）
     * @param <ST>             状态枚举类型参数
     * @return 状态转换结果
     */
    public <ST extends Enum<ST>> StateTransitionResult fire(Enum<?> stateMachineName, Enum<?> event, Long businessId, Map<String, Object> attributes, ST currentStateEnum) {
        return fire(StateMachineName.of(stateMachineName), StateMachineEvent.of(event), businessId, attributes, currentStateEnum);
    }

    /**
     * 触发状态转换（带枚举型当前状态）
     * <p>
     * 使用场景：调用方已知业务的当前状态（如缓存/下游返回），希望显式指定以避免一次读存储。
     * 说明：当显式传入当前状态时，将跳过存储的"当前状态读取"。
     *
     *
     * @param stateMachineName 状态机名称（枚举）
     * @param event            触发事件（枚举）
     * @param businessId       业务唯一标识（Long 类型，支持与业务表的数值型主键直接关联查询）
     * @param currentStateEnum 业务当前状态（枚举，传 null 表示从存储读取）
     * @param <ST>             状态枚举类型参数
     * @return 状态转换结果
     */
    public <ST extends Enum<ST>> StateTransitionResult fire(Enum<?> stateMachineName, Enum<?> event, Long businessId, ST currentStateEnum) {
            return fire(StateMachineName.of(stateMachineName), StateMachineEvent.of(event), businessId, null, currentStateEnum);
    }

    /**
     * 触发状态转换（通用：StateMachineName + StateMachineEvent + businessId）
     * <p>
     * 核心方法，执行完整的状态转换流程：
     * 1. 获取状态机模型和定义
     * 2. 构建状态上下文
     * 3. 查找匹配的转换规则
     * 4. 执行规则引擎（条件判断和动作执行）
     * 5. 保存状态和历史记录
     * 6. 发布事件和同步消息
     *
     *
     * @param stateMachineName  状态机名称包装对象
     * @param event             事件包装对象
     * @param businessId         业务唯一标识
     * @param attributes        额外上下文属性（可为 null）
     * @param currentStateEnum  业务当前状态（枚举，传 null 表示从存储读取）
     * @param <ST>              状态枚举类型参数
     * @return 状态转换结果
     */
private <ST extends Enum<ST>> StateTransitionResult fire(StateMachineName stateMachineName, StateMachineEvent event, Long businessId, Map<String, Object> attributes, ST currentStateEnum) {
        try {
            String stateMachineNameStr = stateMachineName.getStateMachineName();
        StateMachineModel stateMachine = stateMachineRegistry.getStateMachine(stateMachineNameStr);
            if (stateMachine == null) {
                return StateTransitionResult.failure("状态机未找到: " + stateMachineNameStr);
            }
            StateMachineDefinition definition = getStateMachineDefinition(stateMachineNameStr);
            if (definition == null) {
            log.debug("状态机定义未找到: {}，将仅使用存储进行状态管理", stateMachineNameStr);
            }
        String currentState = currentStateEnum == null ? null : currentStateEnum.name();
        StateContext context = new StateContext(currentState, event.getEventName());
        if (attributes != null) {
            attributes.forEach(context::setAttribute);
        }
            if (currentState == null) {
            String storedState = stateStorage.getCurrentState(stateMachineNameStr, businessId);
                if (storedState != null) {
                    context.setCurrentState(storedState);
                } else {
                    context.setCurrentState(stateMachine.getInitialState());
                }
            }
            List<Transition> transitions = stateMachine.getTransitions(context.getCurrentState(), event.getEventName());
            // 终态不可变更：若当前为 FINAL/ERROR，仅允许 attributes.reopen=true 的白名单转换
            State currentStateMeta = stateMachine.getState(context.getCurrentState());
            if (currentStateMeta != null && (currentStateMeta.getType() == State.StateType.FINAL || currentStateMeta.getType() == State.StateType.ERROR)) {
                transitions = transitions.stream()
                        .filter(t -> t.getAttributes() != null && Boolean.TRUE.equals(t.getAttributes().get("reopen")))
                        .toList();
            }
            if (transitions.isEmpty()) {
                return StateTransitionResult.failure("没有找到匹配的转换规则");
            }
            // 设置表达式配置（供 StateTransitionRule 使用）
            ExpressionConfigHolder.setConfig(
                properties.getRulesEngine().getExpression()
            );

            List<StateTransitionRule> rules = new ArrayList<>();
            for (Transition transition : transitions) {
                rules.add(new StateTransitionRule(transition, context));
            }

            // 按优先级排序（如果启用）
            if (properties.getRulesEngine().isPriorityBased() && rules.size() > 1) {
                rules.sort((r1, r2) -> Integer.compare(
                    r2.transition().getPriority(),
                    r1.transition().getPriority()
                ));
                if (log.isDebugEnabled() && properties.getRulesEngine().isEnableExecutionLog()) {
                    log.debug("规则已按优先级排序: {}", rules.stream()
                        .map(r -> "%s:%d".formatted(r.transition().getName(), r.transition().getPriority()))
                        .toList());
                }
            }

            // 使用配置创建规则引擎参数
            RulesEngineParameters parameters = new RulesEngineParameters()
                    .skipOnFirstFailedRule(properties.getRulesEngine().isSkipOnFirstFailedRule())
                    .skipOnFirstAppliedRule(properties.getRulesEngine().isSkipOnFirstAppliedRule())
                    .skipOnFirstNonTriggeredRule(properties.getRulesEngine().isSkipOnFirstNonTriggeredRule())
                    .priorityThreshold(properties.getRulesEngine().getRulePriorityThreshold());
            RulesEngine rulesEngine = new DefaultRulesEngine(parameters);
            Rules rulesSet = new Rules();
            rules.forEach(rulesSet::register);
            Facts facts = new Facts();
            facts.put("context", context);

            // 规则执行（带超时监控）
            long startTime = System.currentTimeMillis();
            long timeoutMs = properties.getRulesEngine().getExecutionTimeoutMs();
            try {
                if (timeoutMs > 0 && properties.getRulesEngine().isEnableExecutionLog()) {
                    log.debug("执行规则引擎，超时阈值: {} ms, 规则数: {}", timeoutMs, rules.size());
                }
            rulesEngine.fire(rulesSet, facts);
                long costMs = System.currentTimeMillis() - startTime;
                if (timeoutMs > 0 && costMs > timeoutMs) {
                    log.warn("规则执行超时: {} ms (阈值: {} ms), 状态机: {}, 业务ID: {}",
                        costMs, timeoutMs, stateMachineNameStr, businessId);
                } else if (properties.getRulesEngine().isEnableExecutionLog() && log.isDebugEnabled()) {
                    log.debug("规则执行完成，耗时: {} ms, 状态机: {}, 业务ID: {}",
                        costMs, stateMachineNameStr, businessId);
                }
            } catch (Exception e) {
                long costMs = System.currentTimeMillis() - startTime;
                log.error("规则执行异常，耗时: {} ms, 状态机: {}, 业务ID: {}",
                    costMs, stateMachineNameStr, businessId, e);
                throw e;
            }
            if (context.getTransition() != null) {
            long seq = assignSeq(stateMachineNameStr, businessId);
            context.setAttribute("seq", seq);
            stateStorage.saveCurrentState(stateMachineNameStr, businessId, context.getCurrentState(), context);
            if (properties.isEnableHistory()) {
                stateStorage.saveStateHistory(stateMachineNameStr, businessId,
                        context.getPreviousState(), context.getCurrentState(), event.getEventName(), context);
            }
            if (properties.isEnableEvents()) {
                // 发布 Spring 事件（兼容其他监听器）
                eventPublisher.publishEvent(StateChangedEvent.from(
                        stateMachineNameStr,
                        businessId,
                        event.getEventName(),
                        context
                ));
            }
            DbPersistenceMode effectiveMode = resolveEffectiveMode(definition, context.getCurrentState(), context.getTransition(), stateMachineNameStr);
            persistToDatabaseByMode(effectiveMode, stateMachineNameStr, businessId, event.getEventName(), context);
                return StateTransitionResult.success(context);
            } else {
                return StateTransitionResult.failure("状态转换失败");
            }
        } catch (StateMachineException e) {
            return StateTransitionResult.failure("状态转换异常[%s]: %s".formatted(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            return StateTransitionResult.failure("状态转换异常: " + e.getMessage());
        }
    }

    /**
     * 获取当前状态（字符串）
     * <p>
     * 从存储中获取指定业务对象的当前状态。如果存储中不存在，则返回状态机的初始状态。
     *
     *
     * @param stateMachineName 状态机名称（枚举）
     * @param businessId       业务唯一标识
     * @return 当前状态（字符串），如果状态机不存在则返回 null
     */
    public String getCurrentState(Enum<?> stateMachineName, Long businessId) {
        return getCurrentState(StateMachineName.of(stateMachineName), businessId);
    }

    /**
     * 获取当前状态（枚举）
     * <p>
     * 从存储中获取指定业务对象的当前状态，并转换为指定的枚举类型。
     * 如果存储中不存在，则返回状态机的初始状态对应的枚举值。
     *
     *
     * @param stateMachineName 状态机名称（枚举）
     * @param businessId       业务唯一标识
     * @param stateEnumClass   目标状态枚举类型
     * @param <ST>             状态枚举类型参数
     * @return 当前状态（枚举），如果状态不存在或转换失败则返回 null
     */
    public <ST extends Enum<ST>> ST getCurrentState(Enum<?> stateMachineName, Long businessId, Class<ST> stateEnumClass) {
        String state = getCurrentState(stateMachineName, businessId);
        return state == null ? null : Enum.valueOf(stateEnumClass, state);
    }

    /**
     * 获取当前状态（通用）
     * <p>
     * 内部方法，从存储中获取当前状态，如果不存在则返回状态机的初始状态。
     *
     *
     * @param stateMachineName 状态机名称包装对象
     * @param businessId       业务唯一标识
     * @return 当前状态（字符串），如果状态机不存在则返回 null
     */
    private String getCurrentState(StateMachineName stateMachineName, Long businessId) {
        String stateMachineNameStr = stateMachineName.getStateMachineName();
    String currentState = stateStorage.getCurrentState(stateMachineNameStr, businessId);
        if (currentState == null) {
        StateMachineModel stateMachine = stateMachineRegistry.getStateMachine(stateMachineNameStr);
            if (stateMachine != null) {
                return stateMachine.getInitialState();
            }
        }
        return currentState;
    }

    /**
     * 检查是否可以执行指定事件
     * <p>
     * 检查指定业务对象在当前状态下是否可以执行指定事件，即是否存在匹配的转换规则。
     * 用于前置校验，避免无效的状态转换尝试。
     *
     *
     * @param stateMachineName 状态机名称（枚举）
     * @param event            触发事件（枚举）
     * @param businessId       业务唯一标识
     * @return true 表示可以执行该事件，false 表示不能执行
     */
    public boolean canTransitionTo(Enum<?> stateMachineName, Enum<?> event, Long businessId) {
    String stateMachineNameStr = StateMachineName.of(stateMachineName).getStateMachineName();
    String currentState = stateStorage.getCurrentState(stateMachineNameStr, businessId);
        if (currentState == null) {
        StateMachineModel stateMachine = stateMachineRegistry.getStateMachine(stateMachineNameStr);
            if (stateMachine != null) {
                currentState = stateMachine.getInitialState();
            } else {
                return false;
            }
        }
    StateMachineModel stateMachine = stateMachineRegistry.getStateMachine(stateMachineNameStr);
        if (stateMachine == null) {
            return false;
        }
    List<Transition> transitions = stateMachine.getTransitions(currentState, StateMachineEvent.of(event).getEventName());
        return !transitions.isEmpty();
    }

    /**
     * 获取状态历史
     * <p>
     * 获取指定业务对象在指定状态机下的所有状态变更历史记录。
     * 用于审计追踪、问题排查、可视化时间线等场景。
     *
     *
     * @param stateMachineName 状态机名称（枚举）
     * @param businessId       业务唯一标识
     * @return 状态历史记录列表，按时间顺序返回（具体顺序取决于存储实现）
     */
    public List<StateHistory> getStateHistory(Enum<?> stateMachineName, Long businessId) {
    String stateMachineNameStr = StateMachineName.of(stateMachineName).getStateMachineName();
    return stateStorage.getStateHistory(stateMachineNameStr, businessId);
    }

    /**
     * 删除指定业务对象在指定状态机下的所有状态数据
     * <p>
     * 使用场景：
     * <ul>
     *     <li>业务侧在执行归档/写库迁移后，显式清理 Redis 中的当前状态与历史列表</li>
     *     <li>长生命周期业务希望在“完结”后主动释放 Redis 占用，而不是仅依赖定时任务</li>
     * </ul>
     *
     * @param stateMachineName 状态机名称（枚举）
     * @param businessId       业务唯一标识
     */
    public void deleteState(Enum<?> stateMachineName, Long businessId) {
        String stateMachineNameStr = StateMachineName.of(stateMachineName).getStateMachineName();
        stateStorage.deleteState(stateMachineNameStr, businessId);
    }

    /**
     * 获取状态机定义
     * 从定义注册表中获取已加载的状态机定义配置
     *
     * @param stateMachineName 状态机名称
     * @return 状态机定义，如果不存在则返回 null
     */
    private StateMachineDefinition getStateMachineDefinition(String stateMachineName) {
        return definitionRegistry.getDefinition(stateMachineName);
    }

    private long assignSeq(String stateMachineName, Long businessId) {
        if (stateDbPersistenceService == null) {
            return System.currentTimeMillis();
        }
        return stateDbPersistenceService.nextSeq(stateMachineName, businessId);
    }

    private DbPersistenceMode resolveEffectiveMode(StateMachineDefinition definition, String targetState, Transition transition, String stateMachineName) {
        DbPersistenceMode machineMode = StatePersistenceModeResolver.resolveMachineMode(definition, properties.getDbPersistenceMode());
        DbPersistenceMode stateMode = StatePersistenceModeResolver.resolveStateMode(definition, targetState).orElse(null);
        DbPersistenceMode effectiveMode = stateMode != null ? stateMode : machineMode;

        if (machineMode == DbPersistenceMode.SYNC && stateMode == DbPersistenceMode.ASYNC) {
            String message = "检测到非法持久化配置（SYNC 降级为 ASYNC）: stateMachine=%s, state=%s".formatted(stateMachineName, targetState);
            if (properties.isStrictPersistenceMode()) {
                throw new IllegalStateException(message);
            }
            log.warn("{}，已强制使用 SYNC", message);
            effectiveMode = DbPersistenceMode.SYNC;
        }

        log.debug("持久化模式决策: stateMachine={}, targetState={}, machineMode={}, stateMode={}, effectiveMode={}",
                stateMachineName, targetState, machineMode, stateMode, effectiveMode);
        return effectiveMode;
    }

    private void persistToDatabaseByMode(DbPersistenceMode mode, String stateMachineName, Long businessId, String eventName, StateContext context) {
        if (mode == DbPersistenceMode.SYNC) {
            if (stateDbPersistenceService == null || !stateDbPersistenceService.isDbPersistenceAvailable()) {
                throw new StateMachineException("STATE_DB_UNAVAILABLE", "SYNC 模式下数据库持久化服务不可用");
            }
            stateDbPersistenceService.persistSync(stateMachineName, businessId, eventName, context);
            return;
        }
        publishAsyncSyncTask(stateMachineName, businessId);
    }

    private void publishAsyncSyncTask(String stateMachineName, Long businessId) {
        StorageType storageType = properties.getStorageType();
        if (storageType == StorageType.REDIS) {
            if (properties.getRedisStream().getDbReplication().isEnabled()) {
                StateSyncMessage syncMessage = StateSyncMessage.of(stateMachineName, businessId);
                String streamKey = keyBuilder.buildDbSyncStreamKey();
                try {
                    StreamMQ.stream().publish(streamKey, syncMessage);
                    log.debug("发布状态同步消息到 Redis Stream: streamKey={}, syncKey={}", streamKey, syncMessage.syncKey());
                } catch (Exception e) {
                    log.error("发布状态同步消息到 Redis Stream 失败: streamKey={}, syncKey={}", streamKey, syncMessage.syncKey(), e);
                }
            }
            return;
        }
        if (storageType == StorageType.ASYNC_THREAD) {
            if (asyncThreadStorageManager != null) {
                try {
                    asyncThreadStorageManager.submitSync(stateMachineName, businessId);
                    log.debug("提交状态同步任务到异步线程池: stateMachine={}, businessId={}", stateMachineName, businessId);
                } catch (Exception e) {
                    log.error("提交状态同步任务到异步线程池失败: stateMachine={}, businessId={}", stateMachineName, businessId, e);
                }
            } else {
                log.warn("异步线程池数据库复制服务未注入，状态同步任务被跳过: stateMachine={}, businessId={}", stateMachineName, businessId);
            }
        }
    }
}
