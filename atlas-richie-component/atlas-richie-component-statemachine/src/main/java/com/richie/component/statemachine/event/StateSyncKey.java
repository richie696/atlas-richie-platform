package com.richie.component.statemachine.event;

import com.richie.component.statemachine.storage.StateMachineKeyBuilder;
import jakarta.annotation.Nonnull;

/**
 * 状态同步键（用于标识需要同步的状态）
 * <p>
 * 包含状态机名称和业务对象ID，用于唯一标识一个状态实例。
 * 
 * <p>
 * <strong>注意：</strong>同步键不包含 Redis key 前缀，只用于消息传递。
 * 如果需要构建完整的 Redis key，请使用 {@link StateMachineKeyBuilder}。
 * 
 * <p>
 * <strong>推荐使用方式：</strong>在 Spring 管理的组件中，优先使用
 * {@link StateMachineKeyBuilder#buildSyncKey(String, Long)}
 * 来构建同步键，以保持 key 生成的一致性。
 * 
 *
 * @author richie696
 * @since 1.0.0
 */
public record StateSyncKey(String stateMachineName, Long businessId) {

    /**
     * 构建同步键字符串
     * <p>
     * <strong>注意：</strong>此方法生成的是同步键（不包含 Redis key 前缀），
     * 仅用于消息传递。如果需要构建完整的 Redis key，请使用 {@link StateMachineKeyBuilder}。
     * 
     * <p>
     * <strong>推荐：</strong>在 Spring 管理的组件中，优先使用
     * {@link StateMachineKeyBuilder#buildSyncKey(String, Long)}。
     * 
     *
     * @param stateMachineName 状态机名称
     * @param businessId       业务对象ID（Long 类型）
     * @return 同步键字符串，格式：stateMachineName:businessId
     */
    public static String build(String stateMachineName, Long businessId) {
        return "%s:%s".formatted(stateMachineName, businessId);
    }

    /**
     * 解析同步键字符串
     *
     * @param str 同步键字符串，格式：stateMachineName:businessId
     * @return 解析后的 StateSyncKey，如果格式不正确或 businessId 无法转换为 Long 则返回 null
     */
    public static StateSyncKey parse(String str) {
        if (str == null || str.isEmpty()) {
            return null;
        }
        String[] parts = str.split(":", 2);
        if (parts.length == 2) {
            try {
                Long businessId = Long.parseLong(parts[1]);
                return new StateSyncKey(parts[0], businessId);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 转换为字符串
     *
     * @return 同步键字符串
     */
    @Override
    @Nonnull
    public String toString() {
        return build(stateMachineName, businessId);
    }
}

