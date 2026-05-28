package com.richie.component.desensitize.core.permission;

import com.richie.component.desensitize.core.config.DesensitizeProperties;
import com.richie.component.desensitize.core.model.MaskContext;

import java.util.Set;

/**
 * 默认权限评估：未启用权限模块时始终脱敏；启用后命中明文角色则跳过脱敏。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
public class DefaultMaskPermissionEvaluator implements MaskPermissionEvaluator {

    /**
     * 依赖组件。
     */
    private final DesensitizeProperties properties;

    /**
     * 构造默认权限评估器。
     *
     * @param properties 脱敏配置
     */
    public DefaultMaskPermissionEvaluator(DesensitizeProperties properties) {
        this.properties = properties;
    }

    /**
     * 判断当前上下文是否需要执行脱敏。
     *
     * @param context 脱敏上下文
     * @return {@code true} 需要脱敏，{@code false} 返回明文
     */
    @Override
    public boolean shouldMask(MaskContext context) {
        DesensitizeProperties.Permission permission = properties.getPermission();
        if (!permission.isEnabled()) {
            return true;
        }
        Set<String> plainRoles = permission.getPlainTextRoles();
        if (plainRoles == null || plainRoles.isEmpty()) {
            return true;
        }
        if (context.roles() == null || context.roles().isEmpty()) {
            return true;
        }
        return context.roles().stream().noneMatch(plainRoles::contains);
    }
}
