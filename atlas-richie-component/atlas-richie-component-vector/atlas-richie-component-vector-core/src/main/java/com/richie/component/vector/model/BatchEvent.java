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

import java.time.Instant;

/**
 * 批量异步事件流协议（sealed interface）。
 * <p>
 * 调用方通过订阅 {@code Flux<BatchEvent>} 获取实时进度，可在 UI 上展示：
 * <ul>
 *   <li>当前批次总进度（succeeded / total）</li>
 *   <li>当前正在处理的记录（itemId + modality + stage）</li>
 *   <li>失败详情（failedStage + Throwable）</li>
 *   <li>批次结束统计（BatchStats）</li>
 * </ul>
 *
 * <h2>事件时序示例（3 条记录，混合模态）</h2>
 * <pre>
 * BatchStarted(b1, 3)
 *   ItemStarted(b1, "f1.txt", TEXT, EMBEDDING)
 *   ItemStarted(b1, "cat.jpg", IMAGE, LOADED)
 *   ItemStarted(b1, "f3.txt", TEXT, EMBEDDING)
 * StageChanged(b1, "f1.txt", TEXT, EMBEDDING, EMBEDDED)
 *   ItemStarted(b1, "f1.txt", TEXT, PERSISTING)
 *   StageChanged(b1, "cat.jpg", IMAGE, LOADED, EMBEDDING)
 *   StageChanged(b1, "f3.txt", TEXT, EMBEDDING, EMBEDDED)
 *   StageChanged(b1, "f1.txt", TEXT, PERSISTING, PERSISTED)
 *   ItemCompleted(b1, "f1.txt", "vec-001", TEXT)
 *   ... (并行交错)
 * BatchCompleted(b1, BatchStats(3, 3, 0, ...))
 * </pre>
 *
 * @author richie696
 * @since 2.0.0
 */
public sealed interface BatchEvent {

    /**
     * @return 事件关联的批次 ID
     */
    String batchId();

    /**
     * @return 事件时间戳
     */
    Instant timestamp();

    // ==================== 批次级事件 ====================

    /**
     * 批次开始 — 调度器接到 Flux 后立即发射。
     */
    record BatchStarted(
            String batchId,
            long total,
            Instant timestamp
    ) implements BatchEvent {}

    /**
     * 批次结束 — 无论成败都发射，是批次的最后一个事件。
     */
    record BatchCompleted(
            String batchId,
            BatchStats stats,
            Instant timestamp
    ) implements BatchEvent {}

    // ==================== 记录级事件 ====================

    /**
     * 单条记录开始处理（即将进入某个阶段）。
     */
    record ItemStarted(
            String batchId,
            String itemId,
            Modality modality,
            Stage stage,
            Instant timestamp
    ) implements BatchEvent {}

    /**
     * 阶段切换事件 — 单条记录从一个 Stage 推进到另一个 Stage 时发射。
     * <p>
     * UI 友好：直接订阅此事件即可在界面上显示"当前阶段"。
     */
    record StageChanged(
            String batchId,
            String itemId,
            Modality modality,
            Stage fromStage,
            Stage toStage,
            Instant timestamp
    ) implements BatchEvent {}

    /**
     * 单条记录完成（写入向量库后立即发射）。
     */
    record ItemCompleted(
            String batchId,
            String itemId,
            String recordId,
            Modality modality,
            Instant timestamp
    ) implements BatchEvent {}

    /**
     * 单条记录失败（默认不中断批次，继续处理其余记录）。
     */
    record ItemFailed(
            String batchId,
            String itemId,
            Stage failedStage,
            Throwable error,
            Instant timestamp
    ) implements BatchEvent {}
}