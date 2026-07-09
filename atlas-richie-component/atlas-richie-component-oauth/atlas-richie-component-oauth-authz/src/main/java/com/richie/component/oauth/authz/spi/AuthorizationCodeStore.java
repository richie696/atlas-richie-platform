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
package com.richie.component.oauth.authz.spi;

import java.util.List;
import java.util.Map;

/**
 * 授权码存储抽象
 * <p>
 * 定义授权码（Authorization Code）的存储与验证契约。
 * 支持 PKCE binding，保证授权码一次性使用。
 *
 * @author richie696
 * @since 2026-06-12
 */
public interface AuthorizationCodeStore {

    /**
     * 存储授权码
     *
     * @param code                 授权码
     * @param clientId            客户端 ID
     * @param redirectUri          重定向 URI
     * @param codeChallenge        PKCE code_challenge
     * @param codeChallengeMethod  PKCE method (S256 或 plain)
     * @param scopes               申请的 scopes
     * @param userId              用户 ID
     * @param ttlSeconds           有效期（秒，默认 600）
     */
    void storeAuthorizationCode(
            String code,
            String clientId,
            String redirectUri,
            String codeChallenge,
            String codeChallengeMethod,
            List<String> scopes,
            String userId,
            long ttlSeconds
    );

    /**
     * 加载授权码
     *
     * @param code 授权码
     * @return Map 包含 client_id, redirect_uri, code_challenge, scopes, user_id 等
     */
    Map<String, String> loadAuthorizationCode(String code);

    /**
     * 消费授权码（一次性使用，调用后删除）
     *
     * @param code 授权码
     */
    void consumeAuthorizationCode(String code);
}
