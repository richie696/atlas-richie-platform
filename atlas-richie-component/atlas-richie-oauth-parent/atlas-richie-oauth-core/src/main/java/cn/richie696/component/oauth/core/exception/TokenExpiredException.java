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
package cn.richie696.component.oauth.core.exception;

import cn.richie696.contract.exception.BusinessException;

/**
 * Access Token 过期异常。
 * <p>
 * 映射为 RFC 6750 的 {@code invalid_token} 错误码(由 {@code error_description} 区分);用于 Resource
 * Server 在 access_token 已过期、需要客户端使用 refresh_token 续期的场景抛出。
 * </p>
 * <p>
 * 处于 oauth-core 的协议错误位置:由 TokenEndpoint、AuthorizationCodeGrant 在 verifyAccessToken
 * 检测到过期时抛出,亦可由 Resource Server 在 introspect / 验签失败时主动抛出;HTTP 适配层统一
 * 映射为 {@link OAuth2ErrorResponse}。
 * </p>
 * <p>
 * 解决的问题:把"过期"这一高频失败模式抽为统一异常,让 Resource Server 可以在拦截器中识别并触发
 * 自动 refresh 流程,而不是当作通用 401 处理。
 * </p>
 *
 * @author richie696
 * @since 2026-06-12
 */
public class TokenExpiredException extends BusinessException {

    public TokenExpiredException() {
        super("invalid_token", "Access token 已过期");
    }
}
