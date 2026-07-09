/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.gateway.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 安全规则枚举
 *
 * @author richie696
 * @version 1.0
 * @since 2023-08-02 00:26:07
 */
@Getter
@RequiredArgsConstructor
public enum SecurityRuleEnum {

    /**
     * 封禁IP
     */
    BANNED_IP("bannedPolicy"),

    /**
     * 自定义HTTP状态码
     */
    CUSTOM_HTTP_STATUS("customHttpStatusPolicy"),

    /**
     * 重定向
     */
    REDIRECT("redirectPolicy");

    private final String policyName;
}
