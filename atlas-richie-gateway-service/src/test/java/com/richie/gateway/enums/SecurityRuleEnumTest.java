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
package com.richie.gateway.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SecurityRuleEnum}.
 */
@DisplayName("SecurityRuleEnum Tests")
class SecurityRuleEnumTest {

    @Nested
    @DisplayName("Enum Values")
    class EnumValues {

        @Test
        @DisplayName("should have BANNED_IP enum value")
        void shouldHaveBannedIpEnumValue() {
            assertThat(SecurityRuleEnum.BANNED_IP).isNotNull();
        }

        @Test
        @DisplayName("should have CUSTOM_HTTP_STATUS enum value")
        void shouldHaveCustomHttpStatusEnumValue() {
            assertThat(SecurityRuleEnum.CUSTOM_HTTP_STATUS).isNotNull();
        }

        @Test
        @DisplayName("should have REDIRECT enum value")
        void shouldHaveRedirectEnumValue() {
            assertThat(SecurityRuleEnum.REDIRECT).isNotNull();
        }

        @Test
        @DisplayName("should have exactly 3 enum values")
        void shouldHaveExactlyThreeEnumValues() {
            assertThat(SecurityRuleEnum.values()).hasSize(3);
        }
    }

    @Nested
    @DisplayName("Policy Name")
    class PolicyNameTests {

        @Test
        @DisplayName("BANNED_IP should have policyName bannedPolicy")
        void bannedIpShouldHavePolicyNameBannedPolicy() {
            assertThat(SecurityRuleEnum.BANNED_IP.getPolicyName()).isEqualTo("bannedPolicy");
        }

        @Test
        @DisplayName("CUSTOM_HTTP_STATUS should have policyName customHttpStatusPolicy")
        void customHttpStatusShouldHavePolicyNameCustomHttpStatusPolicy() {
            assertThat(SecurityRuleEnum.CUSTOM_HTTP_STATUS.getPolicyName()).isEqualTo("customHttpStatusPolicy");
        }

        @Test
        @DisplayName("REDIRECT should have policyName redirectPolicy")
        void redirectShouldHavePolicyNameRedirectPolicy() {
            assertThat(SecurityRuleEnum.REDIRECT.getPolicyName()).isEqualTo("redirectPolicy");
        }
    }

    @Nested
    @DisplayName("ValueOf")
    class ValueOfTests {

        @Test
        @DisplayName("valueOf should return BANNED_IP for BANNED_IP")
        void valueOfShouldReturnBannedIpForBannedIp() {
            assertThat(SecurityRuleEnum.valueOf("BANNED_IP")).isEqualTo(SecurityRuleEnum.BANNED_IP);
        }

        @Test
        @DisplayName("valueOf should return CUSTOM_HTTP_STATUS for CUSTOM_HTTP_STATUS")
        void valueOfShouldReturnCustomHttpStatusForCustomHttpStatus() {
            assertThat(SecurityRuleEnum.valueOf("CUSTOM_HTTP_STATUS")).isEqualTo(SecurityRuleEnum.CUSTOM_HTTP_STATUS);
        }

        @Test
        @DisplayName("valueOf should return REDIRECT for REDIRECT")
        void valueOfShouldReturnRedirectForRedirect() {
            assertThat(SecurityRuleEnum.valueOf("REDIRECT")).isEqualTo(SecurityRuleEnum.REDIRECT);
        }

        @Test
        @DisplayName("valueOf should throw IllegalArgumentException for invalid name")
        void valueOfShouldThrowIllegalArgumentExceptionForInvalidName() {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> SecurityRuleEnum.valueOf("UNKNOWN"));
        }
    }

    @Nested
    @DisplayName("Name and ToString")
    class NameAndToStringTests {

        @Test
        @DisplayName("BANNED_IP name should be BANNED_IP")
        void bannedIpNameShouldBeBannedIp() {
            assertThat(SecurityRuleEnum.BANNED_IP.name()).isEqualTo("BANNED_IP");
        }

        @Test
        @DisplayName("toString should return enum name")
        void toStringShouldReturnEnumName() {
            assertThat(SecurityRuleEnum.BANNED_IP.toString()).isEqualTo("BANNED_IP");
            assertThat(SecurityRuleEnum.CUSTOM_HTTP_STATUS.toString()).isEqualTo("CUSTOM_HTTP_STATUS");
            assertThat(SecurityRuleEnum.REDIRECT.toString()).isEqualTo("REDIRECT");
        }
    }

    @Nested
    @DisplayName("Ordinal")
    class OrdinalTests {

        @Test
        @DisplayName("BANNED_IP should have ordinal 0")
        void bannedIpShouldHaveOrdinal0() {
            assertThat(SecurityRuleEnum.BANNED_IP.ordinal()).isEqualTo(0);
        }

        @Test
        @DisplayName("CUSTOM_HTTP_STATUS should have ordinal 1")
        void customHttpStatusShouldHaveOrdinal1() {
            assertThat(SecurityRuleEnum.CUSTOM_HTTP_STATUS.ordinal()).isEqualTo(1);
        }

        @Test
        @DisplayName("REDIRECT should have ordinal 2")
        void redirectShouldHaveOrdinal2() {
            assertThat(SecurityRuleEnum.REDIRECT.ordinal()).isEqualTo(2);
        }
    }
}
