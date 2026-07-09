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
package com.richie.gateway.dto;

import lombok.Data;

/**
 * 登出请求 DTO
 * <p>
 * 普通访问令牌通过请求头 X-Access-Token 传递，MFA 临时令牌可通过 body 传递。
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
public class LogoutRequest {

    /**
     * MFA 临时令牌（可选，曾走 MFA 流程时传入，用于一并加入黑名单）
     */
    private String mfaToken;
}
