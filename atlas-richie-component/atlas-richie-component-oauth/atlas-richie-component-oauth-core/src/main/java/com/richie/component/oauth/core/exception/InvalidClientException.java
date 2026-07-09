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
package com.richie.component.oauth.core.exception;

import com.richie.contract.exception.BusinessException;

/**
 * 客户端认证失败异常
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
