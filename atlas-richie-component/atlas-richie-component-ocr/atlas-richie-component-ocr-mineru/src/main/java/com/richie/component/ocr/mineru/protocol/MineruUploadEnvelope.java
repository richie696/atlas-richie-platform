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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * MinerU sidecar {@code POST /upload} 端点响应的 wire-format 记录。
 *
 * @author richie696
 * @version 2.2.0
 * @since 2026-07-12
 * @param taskId MinerU 返回的任务唯一标识
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MineruUploadEnvelope(
        @JsonProperty("task_id") String taskId) {
}
