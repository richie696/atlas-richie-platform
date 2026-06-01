package com.richie.component.cache.redis.migration;

import com.richie.context.migration.MigrationWindow;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 单个 {@link MigrationWindow} 字段在 {@code until} 之后仍为 {@code false} 时产生的违规记录。
 *
 * @param ownerClass    持有该字段的类（通常是 {@code @ConfigurationProperties} 静态内部类）
 * @param field         被标注的字段
 * @param window        字段上的 {@link MigrationWindow} 注解
 * @param currentValue  字段当前值
 * @param until         解析后的截止日期
 * @param currentDate   校验时的"今天"（来自注入的 {@code clock}，便于测试）
 * @author Mavis (on behalf of richie696)
 * @since 1.0.0
 */
public record MigrationViolation(
        Class<?> ownerClass,
        Field field,
        MigrationWindow window,
        boolean currentValue,
        LocalDate until,
        LocalDate currentDate
) {

    /**
     * 人类可读的违规描述（用于启动失败时的错误信息）。
     */
    public String describe() {
        return "MigrationWindow expired: %s.%s (owner=%s, until=%s, now=%s, value=%s, removedIn=%s, reason=%s)"
                .formatted(
                        ownerClass.getSimpleName(),
                        field.getName(),
                        window.owner(),
                        until,
                        currentDate,
                        currentValue,
                        window.removedIn(),
                        window.reason());
    }

    /**
     * 校验值的合法性，避免出现字段值非 {@code boolean} 类型的注解误用。
     */
    public void validateShape() {
        Objects.requireNonNull(window, "window");
        if (field.getType() != boolean.class) {
            throw new IllegalStateException(
                    "@MigrationWindow can only be applied to boolean fields, but got "
                            + ownerClass.getName() + "#" + field.getName()
                            + " of type " + field.getType().getName());
        }
    }
}
