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
 * RFC 7662 OAuth Token Introspection 响应模型。
 * <p>
 * 携带 introspection 端点返回的全部字段:{@code active} 标志(唯一必填)、{@code client_id}、
 * {@code token_type}、{@code scope}、{@code exp}、{@code sub}、{@code iss};由
 * {@link cn.richie696.component.oauth.core.TokenEndpoint#introspectToken} 构造,经 OAuth Service
 * 在 HTTP 适配层序列化为 RFC 7662 规定的 JSON。
 * </p>
 * <p>
 * 处于 oauth-core 的协议输出契约位置:TokenEndpoint 是其唯一生产者,Resource Server / 第三方
 * 授权网关是其消费者;同时为审计与日志提供脱敏后的 token 元数据。
 * </p>
 * <p>
 * 解决的问题:把 introspection 端点响应固化为统一模型,避免在协议层各处重复拼 JSON;同时通过
 * 把 active 作为唯一必填字段,允许 Resource Server 用最小数据快速判断 token 是否有效。
 * </p>
 *
 * @author richie696
 * @since 2026-06-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenIntrospection {

    private boolean active;
    private String clientId;
    private String tokenType;
    private String scope;
    private Long expiresIn;
    private String sub;
    private String iss;
}
