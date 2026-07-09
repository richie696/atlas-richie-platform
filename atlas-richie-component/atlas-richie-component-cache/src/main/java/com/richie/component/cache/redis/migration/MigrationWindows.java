/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.cache.redis.migration;

import com.richie.context.migration.MigrationWindow;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * 反射扫描任意配置类的 {@link MigrationWindow} 字段，并基于传入的"今天"判断是否已过期。
 * <p>
 * 纯逻辑、无 Spring 依赖，便于单元测试。
 * <p>
 * 使用方式：
 * <pre>{@code
 * List<MigrationViolation> violations = MigrationWindows.check(
 *         properties.getPerf(),
 *         LocalDate::now
 * );
 * if (!violations.isEmpty()) {
 *     throw new IllegalStateException("Migration windows expired: " + violations);
 * }
 * }</pre>
 *
 * @author Mavis (on behalf of richie696)
 * @since 1.0.0
 */
public final class MigrationWindows {

    private MigrationWindows() {
    }

    /**
     * 扫描 {@code bean} 所在类（含其父类、含静态/实例字段）中所有 {@link MigrationWindow} 字段，
     * 若 {@code now} &gt; {@code until} 且字段值仍为 {@code false}，记录违规。
     *
     * @param bean      配置类实例
     * @param clock     提供"今天"的时间源（测试时可注入固定值）
     * @return 违规列表；若全部合规或没有标记字段则为空
     */
    public static List<MigrationViolation> check(Object bean, Supplier<LocalDate> clock) {
        if (bean == null) {
            return Collections.emptyList();
        }
        LocalDate today = clock.get();
        if (today == null) {
            throw new IllegalArgumentException("clock returned null LocalDate");
        }

        List<MigrationViolation> violations = new ArrayList<>();
        for (Field field : collectAnnotatedFields(bean.getClass())) {
            field.setAccessible(true);
            MigrationWindow window = field.getAnnotation(MigrationWindow.class);
            boolean currentValue;
            try {
                currentValue = field.getBoolean(bean);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(
                        "Cannot read @MigrationWindow field " + bean.getClass().getName()
                                + "#" + field.getName() + " (setAccessible failed)", e);
            }
            if (currentValue) {
                // 已迁移完成，不算违规
                continue;
            }
            LocalDate until;
            try {
                until = LocalDate.parse(window.until());
            } catch (DateTimeParseException e) {
                throw new IllegalStateException(
                        "@MigrationWindow.until() on " + bean.getClass().getName()
                                + "#" + field.getName()
                                + " is not a valid ISO-8601 date: '" + window.until() + "'", e);
            }
            if (today.isAfter(until)) {
                violations.add(new MigrationViolation(
                        bean.getClass(), field, window, currentValue, until, today));
            }
        }
        return violations;
    }

    /**
     * 收集类层级中所有带 {@link MigrationWindow} 的字段（不重复）。
     */
    private static List<Field> collectAnnotatedFields(Class<?> type) {
        List<Field> result = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                if (f.isAnnotationPresent(MigrationWindow.class)) {
                    result.add(f);
                }
            }
            current = current.getSuperclass();
        }
        return result;
    }
}
