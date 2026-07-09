/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.context.migration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个"带迁移窗口"的配置字段：字段当前为旧默认值（{@code false}），但有明确的截止日期。
 * <p>
 * 语义：
 * <ul>
 *   <li>在 {@link #until()} 之前：字段保留旧默认值，业务方可继续使用，但应主动迁移。</li>
 *   <li>到达 {@link #until()} 之后：如果字段值仍为 {@code false}，由
 *       {@code MigrationWindowValidator} 在 Spring 启动时直接抛出异常，拒绝带病启动。</li>
 *   <li>到达 {@link #removedIn()} 之后：字段本身会被物理删除（不再接受配置覆盖），默认值变为
 *       安全值（通常为 {@code true}）。</li>
 * </ul>
 * <p>
 * 适用场景：平台层为存量环境保留"软开关"以提供升级过渡期，但用截止日期硬性约束迁移节奏，
 * 避免"软开关永远没人改"。详见 Redis 性能守卫的 {@code RedisPerf}。
 *
 * @author Mavis (on behalf of richie696)
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface MigrationWindow {

    /**
     * 迁移截止日期，ISO-8601 格式（如 {@code "2027-12-31"}）。
     * <p>到达此日期后，字段值仍为 {@code false} 将导致启动失败。
     *
     * @return ISO-8601 日期字符串
     */
    String until();

    /**
     * 字段将被物理删除的目标版本（语义化版本号，纯文档用途，例如 {@code "2.0.0"}）。
     * <p>到该版本时，字段会从配置类中移除，强制使用安全默认值。
     *
     * @return 语义化版本号
     */
    String removedIn() default "next-major";

    /**
     * 负责人或团队（用于日志与告警聚合）。
     *
     * @return 负责人或团队标识
     */
    String owner();

    /**
     * 迁移原因 / 期望的最终值。
     * <p>例如：{@code "MUST set to true; default flipped in 1.x for new projects"}。
     *
     * @return 人类可读的迁移说明
     */
    String reason();
}
