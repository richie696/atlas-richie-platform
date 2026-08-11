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
package cn.richie696.component.mcp.security.oauth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link McpOAuthPkce} 单元测试：覆盖 RFC 7636 §4 verifier 生成、S256 challenge
 * 派生、{@link McpOAuthPkce#verify(String, String)} 常量时间校验以及输入校验。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpOAuthPkce PKCE S256 helper")
class McpOAuthPkceTest {

    @Nested
    @DisplayName("generateVerifier()")
    class VerifierGeneration {

        @Test
        @DisplayName("输出 43 字符 URL-safe Base64（RFC 7636 §4.1）")
        void rfc7636Format() {
            String verifier = McpOAuthPkce.generateVerifier();

            assertThat(verifier).hasSize(43);
            assertThat(verifier).matches("[A-Za-z0-9_-]+");
        }

        @Test
        @DisplayName("每次调用都生成不同 verifier")
        void uniquenessAcrossInvocations() {
            String first = McpOAuthPkce.generateVerifier();
            String second = McpOAuthPkce.generateVerifier();

            assertThat(first).isNotEqualTo(second);
        }
    }

    @Nested
    @DisplayName("challenge()")
    class ChallengeDerivation {

        @Test
        @DisplayName("同 verifier 派生同 challenge")
        void deterministic() {
            String verifier = McpOAuthPkce.generateVerifier();

            assertThat(McpOAuthPkce.challenge(verifier))
                    .isEqualTo(McpOAuthPkce.challenge(verifier));
        }

        @Test
        @DisplayName("challenge 是 URL-safe Base64 无 padding")
        void challengeFormat() {
            String verifier = McpOAuthPkce.generateVerifier();
            String challenge = McpOAuthPkce.challenge(verifier);

            assertThat(challenge).matches("[A-Za-z0-9_-]+");
            assertThat(challenge).doesNotContain("=");
        }

        @Test
        @DisplayName("verifier 为 null 或 blank 时抛出 IllegalArgumentException")
        void blankVerifierRejected() {
            assertThatThrownBy(() -> McpOAuthPkce.challenge(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("verifier");
            assertThatThrownBy(() -> McpOAuthPkce.challenge(""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("verifier");
            assertThatThrownBy(() -> McpOAuthPkce.challenge("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("verifier");
        }

        @Test
        @DisplayName("MessageDigest 缺失 SHA-256 时抛 IllegalStateException")
        void missingSha256ThrowsIllegalState() throws Exception {
            try (MockedStatic<MessageDigest> mocked = Mockito.mockStatic(MessageDigest.class, Mockito.CALLS_REAL_METHODS)) {
                mocked.when(() -> MessageDigest.getInstance("SHA-256"))
                        .thenThrow(new NoSuchAlgorithmException("SHA-256 missing"));

                assertThatThrownBy(() -> McpOAuthPkce.challenge("verifier"))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("SHA-256 is unavailable");
            }
        }
    }

    @Nested
    @DisplayName("verify()")
    class VerifySemantics {

        @Test
        @DisplayName("同 verifier 派生的 challenge 校验为 true")
        void matchingVerifierReturnsTrue() {
            String verifier = McpOAuthPkce.generateVerifier();
            String challenge = McpOAuthPkce.challenge(verifier);

            assertThat(McpOAuthPkce.verify(verifier, challenge)).isTrue();
        }

        @Test
        @DisplayName("不同 verifier 派生的 challenge 校验为 false")
        void differentVerifierReturnsFalse() {
            String challenge = McpOAuthPkce.challenge(McpOAuthPkce.generateVerifier());

            assertThat(McpOAuthPkce.verify(McpOAuthPkce.generateVerifier(), challenge)).isFalse();
        }

        @Test
        @DisplayName("expectedChallenge 为 null 时返回 false 而不抛异常")
        void nullChallengeReturnsFalse() {
            assertThat(McpOAuthPkce.verify(McpOAuthPkce.generateVerifier(), null)).isFalse();
        }

        @Test
        @DisplayName("expectedChallenge 为空字符串时返回 false")
        void emptyChallengeReturnsFalse() {
            assertThat(McpOAuthPkce.verify(McpOAuthPkce.generateVerifier(), "")).isFalse();
        }
    }
}