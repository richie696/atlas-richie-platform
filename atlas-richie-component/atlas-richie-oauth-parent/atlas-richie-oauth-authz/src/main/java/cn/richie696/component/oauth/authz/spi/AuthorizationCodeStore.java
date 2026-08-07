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
package cn.richie696.component.oauth.authz.spi;

import java.util.List;
import java.util.Map;

/**
 * 授权码(Authorization Code)的存储抽象。
 * <p>
 * 定义授权码的存储、加载、消费契约;支持 PKCE 绑定(code_challenge / code_challenge_method)、
 * OIDC nonce、RFC 8707 resource 绑定,并保证授权码一次性使用 —— 通过原子消费结果
 * {@link AuthorizationCodeConsumeResult} 表达 CONSUMED / NOT_FOUND 两个状态。
 * </p>
 * <p>
 * 处于 oauth-authz 的协议状态接入位置:由 {@link cn.richie696.component.oauth.authz.AuthorizationEndpoint} 写入,被
 * {@link cn.richie696.component.oauth.authz.AuthorizationCodeGrant} 消费;其原子消费语义与 TokenStore 的 refresh_token 消费
 * 同等关键,生产实现必须在分布式锁内完成"读 + 删"。
 * </p>
 * <p>
 * 解决的问题:把授权码的"短期状态 + 一次性消费 + PKCE 绑定"三件事抽象为一个 SPI,业务方替换存储
 * 后端时无需重写并发控制;同时 storeAuthorizationCode 的重载默认实现兼容旧版契约,保证已有
 * 自定义存储实现无需立刻适配 nonce/resource 字段。
 * </p>
 *
 * @author richie696
 * @since 2026-06-12
 */
public interface AuthorizationCodeStore {

    /** 原子消费结果。自定义旧实现未覆盖时由默认方法兼容，但生产实现应覆盖。 */
    default AuthorizationCodeConsumeResult consume(String code) {
        Map<String, String> data = loadAuthorizationCode(code);
        if (data == null || data.isEmpty()) {
            return AuthorizationCodeConsumeResult.notFound();
        }
        consumeAuthorizationCode(code);
        return AuthorizationCodeConsumeResult.consumed(data);
    }

    /**
     * 存储授权码
     *
     * @param code                授权码
     * @param clientId            客户端 ID
     * @param redirectUri         重定向 URI
     * @param codeChallenge       PKCE code_challenge
     * @param codeChallengeMethod PKCE method (S256 或 plain)
     * @param scopes              申请的 scopes
     * @param userId              用户 ID
     * @param ttlSeconds          有效期（秒，默认 600）
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
     * 存储带 OIDC nonce 的授权码。
     *
     * <p>默认回退到旧契约，保证已有自定义 AuthorizationCodeStore 实现无需立即修改。
     * 支持 OIDC 的实现应覆盖此方法并持久化 nonce。</p>
     */
    default void storeAuthorizationCode(
            String code,
            String clientId,
            String redirectUri,
            String codeChallenge,
            String codeChallengeMethod,
            List<String> scopes,
            String userId,
            String nonce,
            long ttlSeconds
    ) {
        storeAuthorizationCode(code, clientId, redirectUri, codeChallenge,
                codeChallengeMethod, scopes, userId, ttlSeconds);
    }

    /**
     * 存储绑定 RFC 8707 resource 和 OIDC nonce 的授权码。
     * <p>默认实现回退到 nonce 版本，兼容已有的自定义存储实现。</p>
     */
    default void storeAuthorizationCode(
            String code,
            String clientId,
            String redirectUri,
            String codeChallenge,
            String codeChallengeMethod,
            List<String> scopes,
            String userId,
            String resource,
            String nonce,
            long ttlSeconds
    ) {
        storeAuthorizationCode(code, clientId, redirectUri, codeChallenge,
                codeChallengeMethod, scopes, userId, nonce, ttlSeconds);
    }

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

    record AuthorizationCodeConsumeResult(Status status, Map<String, String> data) {
        public enum Status { CONSUMED, NOT_FOUND }

        public static AuthorizationCodeConsumeResult consumed(Map<String, String> data) {
            return new AuthorizationCodeConsumeResult(Status.CONSUMED,
                    data == null ? Map.of() : Map.copyOf(data));
        }

        public static AuthorizationCodeConsumeResult notFound() {
            return new AuthorizationCodeConsumeResult(Status.NOT_FOUND, Map.of());
        }
    }
}
