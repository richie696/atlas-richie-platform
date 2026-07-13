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
package com.richie.component.ocr.mineru.protocol;

/**
 * MinerU sidecar 轮询直至完成时返回的最终响应 DTO（record 类型）。
 *
 * <p>由 {@code MineruOcrProvider#pollUntilDone} 在轮询拿到 {@code SUCCEEDED} 时构造、
 * 由 {@code MineruOcrProvider#fromProviderResponse} 消费。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-12
 * @param taskId MinerU 返回的任务唯一标识
 * @param state 任务终态（典型取值 {@code SUCCEEDED}）
 * @param markdown MinerU 输出的结构化 Markdown 全文
 * @param latencyMs 自任务上传起至完成的总耗时，单位毫秒
 */
public record MineruResponse(
        String taskId,
        String state,
        String markdown,
        long latencyMs) {
}