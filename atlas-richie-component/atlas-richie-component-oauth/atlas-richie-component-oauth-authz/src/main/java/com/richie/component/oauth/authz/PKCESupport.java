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
package com.richie.component.oauth.authz;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PKCE S256 挑战生成与验证
 * <p>
 * 支持 OAuth 2.1 规范的 PKCE (Proof Key for Code Exchange) 流程，
 * 仅支持 S256 方法，拒绝 plain 方法。
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
