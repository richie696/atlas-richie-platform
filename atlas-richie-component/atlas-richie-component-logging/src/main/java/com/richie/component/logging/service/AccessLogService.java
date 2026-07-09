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
package com.richie.component.logging.service;

import com.richie.component.cache.local.manage.CacheName;
import com.richie.component.logging.domain.AccessLogInfo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.richie.component.logging.job.PersistentJob;

/**
 * 访问日志服务接口。
 * <p>
 * 扩展 MyBatis-Plus IService，提供将访问日志写入本地缓存并由定时任务批量入库的能力。
 *
 * @author richie696
 * @since 2022-10-09
 */
public interface AccessLogService extends IService<AccessLogInfo> {

    /**
     * 记录访问日志
     * <p>
     *     该方法用于记录访问日志信息，通常在缓存操作（如添加、更新、删除等）时调用。
     *     此处仅将日志记录到本地缓存中，实际的日志持久化会在 {@link PersistentJob}
     *     定时器中批量处理入库操作，如非必要，不建议将操作日志直接入库，以免影响数据库操作的性能。
     *
     * @param cacheName       缓存名称
     * @param accessLogInfo   访问日志信息
     */
    void doRecordLog(CacheName cacheName, AccessLogInfo accessLogInfo);
}
