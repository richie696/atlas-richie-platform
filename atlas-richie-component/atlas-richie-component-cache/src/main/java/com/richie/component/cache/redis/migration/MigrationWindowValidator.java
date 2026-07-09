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

import com.richie.component.cache.redis.config.base.AtlasRedisProperties;
import com.richie.context.migration.MigrationWindow;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 在 Spring 启动阶段扫描 {@link AtlasRedisProperties} 上所有 {@link MigrationWindow} 字段。
 * <p>
 * 启动行为：
 * <ul>
 *   <li>没有任何标记字段：静默通过。</li>
 *   <li>有标记字段且全部已迁移（值为 {@code true}）：INFO 日志，静默通过。</li>
 *   <li>有标记字段已过期且仍为 {@code false}：ERROR 日志列出全部违规，
 *       然后抛出 {@link IllegalStateException}，阻止应用启动。</li>
 * </ul>
 * <p>
 * 设计意图：避免"软开关永远没人改"——给老项目一个明确截止日期，到期后无法绕过。
 *
 * @author Mavis (on behalf of richie696)
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MigrationWindowValidator {

    private final AtlasRedisProperties properties;

    @PostConstruct
    void validate() {
        runValidation(LocalDate::now);
    }

    /**
     * 实际跑校验。包级可见，方便单测直接覆盖。
     *
     * @param clock 时间源
     */
    void runValidation(java.util.function.Supplier<LocalDate> clock) {
        if (properties == null || properties.getPerf() == null) {
            return;
        }
        List<MigrationViolation> violations = MigrationWindows.check(properties.getPerf(), clock);
        if (violations.isEmpty()) {
            log.info("[MigrationWindow] all migration windows are healthy (or no annotated fields)");
            return;
        }
        String detail = violations.stream()
                .map(MigrationViolation::describe)
                .collect(Collectors.joining(" | "));
        log.error("[MigrationWindow] {} violation(s) detected: {}", violations.size(), detail);
        throw new IllegalStateException(
                "Migration windows expired; refusing to start. Violations: " + detail
                        + ". Either set the flagged fields to true in your application.yml, "
                        + "or extend @MigrationWindow.until() with explicit owner approval.");
    }
}
