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
 * Grant 校验失败时的协议异常。
 * <p>
 * 统一映射为 RFC 6749 的 {@code invalid_grant} 错误码,典型场景包括 refresh_token 无效/已过期/
 * 已被使用、授权码无效/已兑换、PKCE 校验失败、resource 不匹配等;由 TokenEndpoint、
 * AuthorizationCodeGrant 等协议服务抛出。
 * </p>
 * <p>
 * 处于 oauth-core 的协议错误位置:与 {@link InvalidClientException} 并列,共同表达 Token 端点的
 * 协议错误;HTTP 适配层统一转换为 {@link OAuth2ErrorResponse}。
 * </p>
 * <p>
 * 解决的问题:把"grant 不合规"这一类协议错误集中到同一异常类型,让"refresh_token 重放检测"、
 * "授权码一次性消费"等安全语义可以通过一个 catch 块拦截并审计。
 * </p>
 *
 * @author richie696
 * @since 2026-06-12
 */
public class InvalidGrantException extends BusinessException {

    public InvalidGrantException(String message) {
        super("invalid_grant", message);
    }
}
