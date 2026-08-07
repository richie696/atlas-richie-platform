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
package cn.richie696.component.oauth.authz;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PKCE S256 挑战生成与验证(拒绝 plain 方法)。
 * <p>
 * 支持 OAuth 2.1 规范的 PKCE(Proof Key for Code Exchange)流程:生成 43-128 位
 * {@code code_verifier}、SHA-256 + Base64URL 派生出 {@code code_challenge}、验签时使用
 * {@link MessageDigest#isEqual} 做时序安全比较,默认拒绝 {@code plain} 方法。
 * </p>
 * <p>
 * 处于 oauth-authz 的协议安全位置:由 {@link AuthorizationEndpoint} 在生成授权码时记录挑战,
 * 由 {@link AuthorizationCodeGrant} 在兑换授权码时校验;同时为 OAuth Service 提供
 * {@link #generateCodeVerifier()} 直接生成 verifier 的入口。
 * </p>
 * <p>
 * 解决的问题:把 PKCE 这一"防截获"的强制校验内建到组件,默认拒绝不安全的 plain 方法;同时用
 * {@code MessageDigest.isEqual} 避免时序攻击,让公开客户端(SPA / 移动端)即使在不可信信道也能
 * 安全完成 authorization_code 流程。
 * </p>
 *
 * @author richie696
 * @since 2026-06-12
 */
@Slf4j
public class PKCESupport {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 生成 PKCE code_verifier
     * <p>
     * 生成 43-128 位的随机字符串，使用 Base64 URL 编码
     *
     * @return 43-128 位随机字符串
     */
    public String generateCodeVerifier() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 生成 PKCE code_challenge (S256)
     * <p>
     * 使用 SHA-256 哈希 code_verifier，然后 Base64 URL 编码（不带填充）
     *
     * @param codeVerifier code_verifier
     * @return BASE64URL(SHA256(code_verifier))
     * @throws IllegalArgumentException 如果 code_verifier 为空
     */
    public String generateCodeChallenge(String codeVerifier) {
        if (codeVerifier == null || codeVerifier.isBlank()) {
            throw new IllegalArgumentException("code_verifier 不能为空");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 算法不可用", e);
        }
    }

    /**
     * 验证 PKCE code_challenge 与 code_verifier 匹配
     * <p>
     * 仅支持 S256 方法，使用时序安全比较
     *
     * @param codeChallenge       code_challenge
     * @param codeChallengeMethod method (必须为 S256)
     * @param codeVerifier        code_verifier
     * @return 是否匹配
     */
    public boolean verifyChallenge(String codeChallenge, String codeChallengeMethod, String codeVerifier) {
        if (codeChallenge == null || codeVerifier == null) {
            return false;
        }
        if (!"S256".equalsIgnoreCase(codeChallengeMethod)) {
            log.warn("不支持的 PKCE method: {}", codeChallengeMethod);
            return false;
        }
        String expectedChallenge = generateCodeChallenge(codeVerifier);
        return MessageDigest.isEqual(
                codeChallenge.getBytes(StandardCharsets.UTF_8),
                expectedChallenge.getBytes(StandardCharsets.UTF_8)
        );
    }
}
