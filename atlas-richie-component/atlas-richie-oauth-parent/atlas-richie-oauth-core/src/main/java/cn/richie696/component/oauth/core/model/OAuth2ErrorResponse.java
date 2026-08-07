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
 * OAuth 2.1 标准错误响应模型。
 * <p>
 * 对应 RFC 6749 §5.2 的错误响应字段:{@code error} 协议错误码、{@code error_description} 人类可读
 * 描述、可选 {@code error_uri} 指向错误文档;由 OAuth Service 在 HTTP 适配层序列化为 JSON 后
 * 返回给客户端。
 * </p>
 * <p>
 * 处于 oauth-core 的协议输出契约位置:由 TokenEndpoint、AuthorizationEndpoint、AuthorizationCodeGrant
 * 等所有协议服务在抛出 {@code BusinessException} 后由 HTTP 适配层统一映射为该对象;错误文档基地址
 * 由 {@link cn.richie696.component.oauth.core.config.OAuth2Properties#getErrorDocsBaseUri()} 控制。
 * </p>
 * <p>
 * 解决的问题:把协议错误响应固化为统一模型,避免每个端点自行拼装 JSON;同时把 error_uri 这一可选
 * 字段留空控制,让运维可以通过配置批量启用错误文档链接而无需改协议层代码。
 * </p>
 *
 * @author richie696
 * @since 2026-06-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuth2ErrorResponse {

    private String error;
    private String errorDescription;
    private String errorUri;
}
