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
package com.richie.component.logging.job;

import com.richie.component.cache.local.manage.LocalCache;
import com.richie.component.logging.config.OperateLogProperties;
import com.richie.component.logging.domain.AccessLogInfo;
import com.richie.component.logging.service.AccessLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.richie.component.cache.enums.L2CachingRegion.ACCESS_LOG;

/**
 * 定时持久化访问日志
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-16 03:12:39
 */
@Component
@RequiredArgsConstructor
public class PersistentJob {

    private final OperateLogProperties properties;
    private final AccessLogService accessLogService;

    /**
     * 定时将本地缓存中的访问日志批量持久化到数据库（每分钟执行一次）。
     */
    @Scheduled(cron = "0 0/1 * * * ?")
    public void persistentAccessLog() {
        if (!properties.isDbPersistent()) {
            return;
        }

        Map<String, AccessLogInfo> accessLogMap = LocalCache.popByCount(ACCESS_LOG, properties.getDbBatchSize());
        if (accessLogMap.isEmpty()) {
            return;
        }
        accessLogService.saveBatch(accessLogMap.values());
    }
}
