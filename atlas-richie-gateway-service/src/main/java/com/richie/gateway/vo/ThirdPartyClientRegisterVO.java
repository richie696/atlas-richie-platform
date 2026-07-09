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
package com.richie.gateway.vo;

import com.google.common.base.MoreObjects;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 第三方客户端注册（测试用）返回 VO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThirdPartyClientRegisterVO {

    /**
     * 客户端ID（即 client_id）
     */
    @JsonProperty("client_id")
    private String clientId;

    /**
     * 客户端密钥（即 client_secret）
     */
    @JsonProperty("client_secret")
    private String clientSecret;
}
