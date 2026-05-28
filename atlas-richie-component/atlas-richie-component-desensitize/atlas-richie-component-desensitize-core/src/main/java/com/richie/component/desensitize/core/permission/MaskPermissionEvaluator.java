package com.richie.component.desensitize.core.permission;

import com.richie.component.desensitize.core.model.MaskContext;

/**
 * 判断是否应对当前上下文执行脱敏。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
@FunctionalInterface
public interface MaskPermissionEvaluator {

    /**
     * 判断当前上下文是否应执行脱敏。
     *
     * @param context 脱敏上下文
     * @return {@code true} 表示需要脱敏；{@code false} 表示返回明文
     */
    boolean shouldMask(MaskContext context);
}
