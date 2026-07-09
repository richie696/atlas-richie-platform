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
package com.richie.component.mongodb.observability;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MongoDB 慢查询日志记录器
 *
 * <p>当 MongoDB 操作耗时超过阈值（200ms）时记录警告日志</p>
 */
@Component
@Slf4j
public class MongodbSlowQueryLogger {

    private static final long THRESHOLD_MS = 200L;

    public void logIfSlow(String collection, String operation, long durationMs) {
        if (durationMs > THRESHOLD_MS) {
            log.warn("Slow MongoDB operation: collection={} op={} duration={}ms",
                    collection, operation, durationMs);
        }
    }
}
