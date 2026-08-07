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
package cn.richie696.component.oauth.core.model;

/**
 * OAuth 2.1 Grant Type 协议值枚举。
 * <p>
 * 表达 RFC 6749 / RFC 8628 中授权服务器可识别的 grant_type 协议字符串与枚举之间的双向映射;
 * 当前内置 {@code client_credentials}/{@code refresh_token}/{@code authorization_code} 三种,
 * {@code device_code} 的协议字符串由 {@link cn.richie696.component.oauth.contract.OAuthGrantTypes}
 * 单独管理(授权入口不同)。
 * </p>
 * <p>
 * 处于 oauth-core 的协议常量位置:被 TokenEndpoint、AuthorizationCodeGrant 等协议入口用于协议
 * 字符串 ↔ 枚举的双向转换,同时为策略路由、灰度发布等横切逻辑提供可枚举的类型。
 * </p>
 * <p>
 * 解决的问题:把"协议里写死的字符串"集中为强类型枚举,避免散落的 magic string 与拼写错误;同时
 * 通过统一的 fromValue 入口,把"未知 grant_type"这种协议错误前置为 IllegalArgumentException。
 * </p>
 *
 * @author richie696
 * @since 2026-06-12
 */
public enum GrantType {

    CLIENT_CREDENTIALS("client_credentials"),
    REFRESH_TOKEN("refresh_token"),
    AUTHORIZATION_CODE("authorization_code");

    private final String value;

    GrantType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static GrantType fromValue(String value) {
        for (GrantType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported grant_type: " + value);
    }
}
