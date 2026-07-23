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
package com.richie.component.vector.model;

/**
 * 索引生命周期状态。
 *
 * @author richie696
 * @since 2.0.0
 */
public enum IndexStatus {

    /** 创建中 */
    CREATING,

    /** 已就绪，可读写 */
    READY,

    /** 配置更新中 */
    UPDATING,

    /** 删除中 */
    DELETING,

    /** 失败 */
    FAILED,

    /** 状态未知（provider 不支持或查询失败） */
    UNKNOWN
}