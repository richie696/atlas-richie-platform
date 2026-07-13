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
package com.richie.component.ocr.baidu.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 百度智能云 OAuth2 {@code /oauth/2.0/token} 端点响应的 wire-format 记录.
 *
 * <p>包含 {@code access_token} / {@code expires_in} / 错误时的 {@code error} 字段。由
 * {@code HttpResponse.bodyAs(BaiduTokenResponse.class)} 一行代码反序列化，避免手动 JsonNode 树遍历。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-12
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BaiduTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") Long expiresIn,
        @JsonProperty("error") String error,
        @JsonProperty("error_description") String errorDescription) {
}
