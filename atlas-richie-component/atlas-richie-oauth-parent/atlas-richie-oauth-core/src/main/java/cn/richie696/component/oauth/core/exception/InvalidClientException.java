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
 * 客户端认证失败时的协议异常。
 * <p>
 * 统一映射为 RFC 6749 的 {@code invalid_client} 错误码,在 HTTP 适配层被序列化为
 * {@link cn.richie696.component.oauth.core.model.OAuth2ErrorResponse};由 {@link cn.richie696.component.oauth.core.ClientAuthenticationService} 与 TokenEndpoint 抛出,
 * 表示客户端不存在、被禁用、Secret 不匹配或认证方式不匹配。
 * </p>
 * <p>
 * 处于 oauth-core 的协议错误位置:协议层抛出的统一入口,被 HTTP 适配层兜底捕获并转换为标准错误
 * 响应;同时为审计与日志提供"客户端认证失败"这一可观测事件。
 * </p>
 * <p>
 * 解决的问题:把"客户端认证失败"集中为一个异常类型,让协议层不必关心 HTTP 表达;同时让
 * Security/限流/审计可以统一拦截该异常类型做横切处理。
 * </p>
 *
 * @author richie696
 * @since 2026-06-12
 */
public class InvalidClientException extends BusinessException {

    public InvalidClientException(String clientId) {
        super("invalid_client", "客户端认证失败: %s".formatted(clientId));
    }

    public InvalidClientException(String errorCode, String message) {
        super(errorCode, message);
    }
}
