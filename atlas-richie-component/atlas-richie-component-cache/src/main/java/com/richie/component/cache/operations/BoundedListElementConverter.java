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
package com.richie.component.cache.operations;

import com.richie.context.utils.data.JsonUtils;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 有界队列/栈读取时的元素反序列化；失败时记录 WARN 便于排查（批量读取会跳过坏元素）。
 *
 * @author richie696
 * @since 2026-06-04
 */
@Slf4j
final class BoundedListElementConverter {


    private BoundedListElementConverter() {
    }

    /**
     * 单次读取：Redis 无元素时 {@code raw} 为 null，静默返回 null；有元素但反序列化失败时打 WARN 并返回 null。
     */
    @Nullable
    static <T> T convertOne(@Nullable Object raw, String key, Class<T> clazz, String operation) {
        if (raw == null) {
            return null;
        }
        ConvertOutcome<T> outcome = tryConvert(raw, clazz);
        if (outcome.isSuccess()) {
            return Objects.requireNonNull(outcome.converted(), "converted");
        }
        log.warn(
                "有界列表读取反序列化失败: key={}, operation={}, type={}, failure={}",
                key, operation, clazz.getSimpleName(), Objects.requireNonNull(outcome.failureReason(), "failureReason"));
        return null;
    }

    static <T> List<T> convertAll(List<?> raw, String key, Class<T> clazz, String operation) {
        var result = new ArrayList<T>(raw.size());
        int dropped = 0;
        String firstReason = null;
        for (int i = 0; i < raw.size(); i++) {
            ConvertOutcome<T> outcome = tryConvert(raw.get(i), clazz);
            if (outcome.isSuccess()) {
                result.add(Objects.requireNonNull(outcome.converted(), "converted"));
            } else {
                dropped++;
                if (firstReason == null) {
                    firstReason = "index=%d %s".formatted(i, Objects.requireNonNull(outcome.failureReason(), "failureReason"));
                }
            }
        }
        if (dropped > 0) {
            log.warn("有界列表批量读取丢弃 {} 个元素（null 或反序列化失败）: key={}, operation={}," +
                            " type={}, rawCount={}, returned={}, firstFailure={}",
                    dropped, key, operation, clazz.getSimpleName(), raw.size(),
                    result.size(), firstReason);
        }
        return result;
    }

    private static <T> ConvertOutcome<T> tryConvert(@Nullable Object element, Class<T> clazz) {
        if (element == null) {
            return ConvertOutcome.failure("element=null");
        }
        try {
            T converted = JsonUtils.getInstance().convertObject(element, clazz);
            if (converted == null) {
                return ConvertOutcome.failure("convertResult=null");
            }
            return ConvertOutcome.success(converted);
        } catch (RuntimeException ex) {
            String message = ex.getMessage();
            return ConvertOutcome.failure("error=" + (message != null ? message : ex.getClass().getSimpleName()));
        }
    }

    private record ConvertOutcome<T>(@Nullable T converted, @Nullable String failureReason) {
        static <T> ConvertOutcome<T> success(T converted) {
            return new ConvertOutcome<>(Objects.requireNonNull(converted, "converted"), null);
        }

        static <T> ConvertOutcome<T> failure(String failureReason) {
            return new ConvertOutcome<>(null, Objects.requireNonNull(failureReason, "failureReason"));
        }

        boolean isSuccess() {
            return failureReason == null;
        }
    }
}
