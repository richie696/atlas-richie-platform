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
 * MinerU sidecar {@code GET /tasks/{id}} 端点响应的 wire-format 记录。
 *
 * <p>所有字段都可空（视轮询阶段而定）；{@code state} 终态为 {@code SUCCEEDED} 或 {@code FAILED}。
 *
 * @param state 任务状态
 * @param markdown 任务结果的 markdown 格式文本
 * @param errorMsg 错误信息
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-12
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MineruPollEnvelope(
        @JsonProperty("state") String state,
        @JsonProperty("markdown") String markdown,
        @JsonProperty("error_msg") String errorMsg) {
}
