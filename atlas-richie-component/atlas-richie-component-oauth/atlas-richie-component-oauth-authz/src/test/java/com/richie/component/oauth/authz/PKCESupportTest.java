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
package com.richie.component.oauth.authz;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PKCESupport 测试")
class PKCESupportTest {

    private PKCESupport pkceSupport;

    @BeforeEach
    void setUp() {
        pkceSupport = new PKCESupport();
    }

    @Test
    @DisplayName("generateCodeVerifier 返回 43 字符字符串")
    void generateCodeVerifier_returns43Chars() {
        String verifier = pkceSupport.generateCodeVerifier();

        assertThat(verifier).isNotNull();
        assertThat(verifier).hasSize(43);
    }

    @Test
    @DisplayName("generateCodeVerifier 每次调用返回不同值")
    void generateCodeVerifier_returnsDifferentValuesOnEachCall() {
        String verifier1 = pkceSupport.generateCodeVerifier();
        String verifier2 = pkceSupport.generateCodeVerifier();
        String verifier3 = pkceSupport.generateCodeVerifier();

        assertThat(verifier1).isNotEqualTo(verifier2);
        assertThat(verifier2).isNotEqualTo(verifier3);
        assertThat(verifier1).isNotEqualTo(verifier3);
    }

    @Test
    @DisplayName("generateCodeChallenge 对相同输入产生一致输出")
    void generateCodeChallenge_producesConsistentOutput() {
        String verifier = pkceSupport.generateCodeVerifier();

        String challenge1 = pkceSupport.generateCodeChallenge(verifier);
        String challenge2 = pkceSupport.generateCodeChallenge(verifier);
        String challenge3 = pkceSupport.generateCodeChallenge(verifier);

        assertThat(challenge1).isEqualTo(challenge2);
        assertThat(challenge2).isEqualTo(challenge3);
    }

    @Test
    @DisplayName("generateCodeChallenge 对 null 输入抛出异常")
    void generateCodeChallenge_throwsOnNull() {
        assertThatThrownBy(() -> pkceSupport.generateCodeChallenge(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("code_verifier 不能为空");
    }

    @Test
    @DisplayName("generateCodeChallenge 对空白输入抛出异常")
    void generateCodeChallenge_throwsOnBlank() {
        assertThatThrownBy(() -> pkceSupport.generateCodeChallenge("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("code_verifier 不能为空");
    }

    @Test
    @DisplayName("verifyChallenge 对匹配的 S256 challenge 返回 true")
    void verifyChallenge_returnsTrueForMatchingS256Challenge() {
        String verifier = pkceSupport.generateCodeVerifier();
        String challenge = pkceSupport.generateCodeChallenge(verifier);

        boolean result = pkceSupport.verifyChallenge(challenge, "S256", verifier);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("verifyChallenge 当 codeChallenge 为 null 时返回 false")
    void verifyChallenge_returnsFalseWhenCodeChallengeIsNull() {
        String verifier = pkceSupport.generateCodeVerifier();

        boolean result = pkceSupport.verifyChallenge(null, "S256", verifier);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("verifyChallenge 当 codeVerifier 为 null 时返回 false")
    void verifyChallenge_returnsFalseWhenCodeVerifierIsNull() {
        String challenge = pkceSupport.generateCodeChallenge("anyVerifier");

        boolean result = pkceSupport.verifyChallenge(challenge, "S256", null);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("verifyChallenge 对非 S256 method 返回 false")
    void verifyChallenge_returnsFalseForNonS256Method() {
        String verifier = pkceSupport.generateCodeVerifier();
        String challenge = pkceSupport.generateCodeChallenge(verifier);

        boolean result = pkceSupport.verifyChallenge(challenge, "plain", verifier);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("verifyChallenge 对不匹配的值返回 false")
    void verifyChallenge_returnsFalseForMismatchedValues() {
        String verifier1 = pkceSupport.generateCodeVerifier();
        String verifier2 = pkceSupport.generateCodeVerifier();
        String challenge = pkceSupport.generateCodeChallenge(verifier1);

        boolean result = pkceSupport.verifyChallenge(challenge, "S256", verifier2);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("S256 challenge 长度应为 43 字符")
    void s256Challenge_hasCorrectLength() {
        String verifier = pkceSupport.generateCodeVerifier();
        String challenge = pkceSupport.generateCodeChallenge(verifier);

        assertThat(challenge).hasSize(43);
    }
}
