/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.storage.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 访问控制列表（ACL）类型枚举
 * <p>
 * 统一的 ACL 类型定义，各存储引擎通过转换器转换为对应的 ACL 值
 *
 * @author richie696
 * @version 1.0
 * @since 2025-01-XX
 */
@Getter
@RequiredArgsConstructor
public enum AclTypeEnum {

    /**
     * 私有（默认，仅所有者可访问）
     */
    PRIVATE("私有"),

    /**
     * 公共读（所有人可读，仅所有者可写）
     */
    PUBLIC_READ("公共读"),

    /**
     * 公共读写（所有人可读写）
     */
    PUBLIC_READ_WRITE("公共读写"),

    /**
     * 认证用户读（仅认证用户可读）
     */
    AUTHENTICATED_READ("认证用户读"),

    /**
     * 桶所有者读（桶所有者可读）
     */
    BUCKET_OWNER_READ("桶所有者读"),

    /**
     * 桶所有者完全控制（桶所有者完全控制）
     */
    BUCKET_OWNER_FULL_CONTROL("桶所有者完全控制"),

    /**
     * 日志传递写（用于日志传递）
     */
    LOG_DELIVERY_WRITE("日志传递写");

    private final String description;

}

