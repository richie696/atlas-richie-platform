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
package com.richie.component.cache.enums;

import com.richie.component.cache.local.manage.CacheName;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 全局缓存的二级缓存区域名称枚举类
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-17 17:11:39
 */
@Getter
@RequiredArgsConstructor
public enum L2CachingRegion implements CacheName {

    /** 全局缓存区域（通用 KV 等） */
    GLOBAL_CACHE("global_cache"),

    /** 访问日志专用缓存区域 */
    ACCESS_LOG("access_log");

    /** 区域名称（对应 JSR-107 Cache 名称） */
    private final String cache;

}
