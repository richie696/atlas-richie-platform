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
package com.richie.component.storage.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 存储类型枚举
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-03 11:29:52
 */
@Getter
@RequiredArgsConstructor
public enum StorageTypeEnum {

    /**
     * 标准存储
     */
    STANDARD("标准存储"),
    /**
     * 低频访问存储
     */
    STANDARD_IA("低频访问存储"),
    /**
     * 单区低频访问存储(仅S3)
     */
    ONEZONE_IA("单区低频访问存储"),
    /**
     * 归档存储
     */
    ARCHIVE("归档存储"),
    /**
     * 归档闪回存储
     */
    ARCHIVE_FR("归档闪回存储"),
    /**
     * 冷归档存储
     */
    COLD_ARCHIVE("冷归档存储"),
    /**
     * 深冷归档存储
     */
    DEEP_COLD_ARCHIVE("深冷归档存储"),
    /**
     * 智能分层存储
     */
    INTELLIGENT_TIERING("智能分层存储"),
    /**
     * 多可用区标准存储(仅COS)
     */
    MULTI_AZ_STANDARD("多可用区标准存储"),
    /**
     * 多可用区低频访问存储(仅COS)
     */
    MULTI_AZ_STANDARD_IA("多可用区低频访问存储"),
    /**
     * 多可用区归档存储(仅COS)
     */
    MULTI_AZ_ARCHIVE("多可用区归档存储"),
    /**
     * 多可用区冷归档存储(仅COS)
     */
    MULTI_AZ_COLD_ARCHIVE("多可用区冷归档存储"),
    /**
     * 多可用区深冷归档存储(仅COS)
     */
    MULTI_AZ_DEEP_COLD_ARCHIVE("多可用区深冷归档存储"),
    /**
     * 多可用区智能分层存储(仅COS)
     */
    MULTI_AZ_INTELLIGENT_TIERING("多可用区智能分层存储"),
    /**
     * 降低冗余存储(仅S3)
     */
    REDUCED_REDUNDANCY("降低冗余存储(仅S3)"),
    /**
     * 冰川存储(仅S3)
     */
    GLACIER("冰川存储(仅S3)"),
    /**
     * 冰川即时存取存储(仅S3)
     */
    GLACIER_IR("冰川即时存取存储(仅S3)"),
    /**
     * 雪存储(仅S3)
     */
    SNOW("雪存储(仅S3)"),
    /**
     * 本地存储(仅S3)
     */
    Outposts("本地存储(仅S3)");

    private final String description;

}
