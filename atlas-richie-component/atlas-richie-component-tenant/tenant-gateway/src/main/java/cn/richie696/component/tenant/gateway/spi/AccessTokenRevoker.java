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
package cn.richie696.component.tenant.gateway.spi;

import reactor.core.publisher.Mono;

/**
 * Gateway 访问令牌作废端口。
 *
 * <p>租户拦截器只负责在发现租户不可用时调用该端口，具体的黑名单、缓存或
 * 其它 Token 撤销机制由 Gateway 提供。</p>
 */
@FunctionalInterface
public interface AccessTokenRevoker {

    /**
     * 作废访问令牌。
     *
     * @param token 访问令牌
     */
    Mono<Void> revoke(String token);
}
