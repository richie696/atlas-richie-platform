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
package com.richie.component.ocr.paddlevl.protocol;

/**
 * PaddleVl Provider 内部传输用 envelope —— 已 typed 反序列化的 poll 响应与耗时。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-11
 * @param taskId sidecar 返回的任务唯一标识
 * @param state 任务终态（{@code SUCCEEDED} / {@code FAILED}）
 * @param poll 已 typed 反序列化的轮询响应 envelope（{@code SUCCEEDED} 时非空）
 * @param latencyMs 自任务提交起至完成的总耗时
 */
public record VlResponse(
        String taskId,
        String state,
        VlSubmitEnvelope.VlPollEnvelope poll,
        long latencyMs) {
}
