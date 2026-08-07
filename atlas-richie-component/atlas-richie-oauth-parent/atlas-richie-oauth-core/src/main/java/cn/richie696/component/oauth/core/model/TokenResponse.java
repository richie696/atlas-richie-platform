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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OAuth 2.1 Token 端点响应模型。
 * <p>
 * 描述 RFC 6749 §5.1 规定的成功响应字段:{@code access_token}、{@code token_type}(固定为 Bearer)、
 * {@code expires_in}、可选 {@code refresh_token}、{@code scope};由 TokenEndpoint、
 * AuthorizationCodeGrant 在签发成功后构造,由 OAuth Service 在 HTTP 适配层序列化为 JSON 返回。
 * </p>
 * <p>
 * 处于 oauth-core 的协议输出契约位置:Token 端点的所有 grant 路径(client_credentials /
 * refresh_token / device_code / authorization_code)最终汇聚为该对象,是协议内核对外的统一
 * 协议响应。
 * </p>
 * <p>
 * 解决的问题:用统一模型收敛所有 grant 的成功响应,避免在协议层重复定义响应结构;同时通过把
 * scope 显式建模,让 client_credentials 之外的 grant 能把"本次实际授予的 scope"回传给客户端,
 * 解决 RFC 6749 §5.1 关于 scope 必须回传的要求。
 * </p>
 *
 * @author richie696
 * @since 2026-06-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {

    private String accessToken;
    private String tokenType;
    private Long expiresIn;
    private String refreshToken;
    private String scope;
}
