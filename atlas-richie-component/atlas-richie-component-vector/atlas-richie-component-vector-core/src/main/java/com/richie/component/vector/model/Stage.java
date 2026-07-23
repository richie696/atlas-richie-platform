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
 * 批量处理阶段。
 * <p>
 * 表示单条记录在批量异步管线中的当前状态，对应 {@link BatchEvent} 中 {@code StageChanged} 事件的 {@code toStage} 字段。
 *
 * @author richie696
 * @since 2.0.0
 */
public enum Stage {

    /** 已接收，未调度 */
    QUEUED,

    /** 图片已下载/读取到内存（文本跳过） */
    LOADED,

    /** 调用嵌入模型中 */
    EMBEDDING,

    /** 嵌入完成 */
    EMBEDDED,

    /** 写入向量库中 */
    PERSISTING,

    /** 已写入向量库 */
    PERSISTED,

    /** 全部完成 */
    COMPLETED,

    /** 失败（终态） */
    FAILED
}