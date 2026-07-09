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
package com.richie.gateway.constants;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GatewayRedisKey}.
 */
@DisplayName("GatewayRedisKey Tests")
class GatewayRedisKeyTest {

    @Nested
    @DisplayName("Enum Values")
    class EnumValues {

        @Test
        @DisplayName("should have 27 enum values")
        void shouldHaveTwentySevenEnumValues() {
            assertThat(GatewayRedisKey.values()).hasSize(27);
        }

        @Test
        @DisplayName("should find enum by name")
        void shouldFindEnumByName() {
            assertThat(GatewayRedisKey.valueOf("OAUTH2_CLIENT_CONFIG")).isNotNull();
            assertThat(GatewayRedisKey.valueOf("GATEWAY_API_INDEX")).isNotNull();
            assertThat(GatewayRedisKey.valueOf("SECURITY_PERMANENT_BAN")).isNotNull();
        }
    }

    @Nested
    @DisplayName("GetPrefix")
    class GetPrefixTests {

        @Test
        @DisplayName("should return correct prefix for OAUTH2_CLIENT_CONFIG")
        void shouldReturnCorrectPrefixForOauth2ClientConfig() {
            assertThat(GatewayRedisKey.OAUTH2_CLIENT_CONFIG.getPrefix()).isEqualTo("third-party-client:");
        }

        @Test
        @DisplayName("should return correct prefix for OAUTH2_AUDIT_EVENTS")
        void shouldReturnCorrectPrefixForOauth2AuditEvents() {
            assertThat(GatewayRedisKey.OAUTH2_AUDIT_EVENTS.getPrefix()).isEqualTo("oauth2:audit:events");
        }

        @Test
        @DisplayName("should return correct prefix for GATEWAY_API_INDEX")
        void shouldReturnCorrectPrefixForGatewayApiIndex() {
            assertThat(GatewayRedisKey.GATEWAY_API_INDEX.getPrefix()).isEqualTo("gateway:api:index");
        }

        @Test
        @DisplayName("should return correct prefix for ANOMALY_RATELIMIT")
        void shouldReturnCorrectPrefixForAnomalyRatelimit() {
            assertThat(GatewayRedisKey.ANOMALY_RATELIMIT.getPrefix()).isEqualTo("gateway:anomaly:ratelimit:");
        }
    }

    @Nested
    @DisplayName("GetKey No Params")
    class GetKeyNoParamsTests {

        @Test
        @DisplayName("should return prefix when no params for static key")
        void shouldReturnPrefixWhenNoParamsForStaticKey() {
            assertThat(GatewayRedisKey.OAUTH2_AUDIT_EVENTS.getKey()).isEqualTo("oauth2:audit:events");
        }

        @Test
        @DisplayName("should return prefix when no params for parameterized key")
        void shouldReturnPrefixWhenNoParamsForParameterizedKey() {
            assertThat(GatewayRedisKey.OAUTH2_CLIENT_CONFIG.getKey()).isEqualTo("third-party-client:");
        }
    }

    @Nested
    @DisplayName("GetKey Single Param")
    class GetKeySingleParamTests {

        @Test
        @DisplayName("should format single param correctly for OAUTH2_CLIENT_CONFIG")
        void shouldFormatSingleParamCorrectlyForOauth2ClientConfig() {
            assertThat(GatewayRedisKey.OAUTH2_CLIENT_CONFIG.getKey("client-123"))
                    .isEqualTo("third-party-client:client-123");
        }

        @Test
        @DisplayName("should format single param correctly for OAUTH2_REFRESH_TOKEN")
        void shouldFormatSingleParamCorrectlyForOauth2RefreshToken() {
            assertThat(GatewayRedisKey.OAUTH2_REFRESH_TOKEN.getKey("token-abc"))
                    .isEqualTo("refresh-token:token-abc");
        }

        @Test
        @DisplayName("should format single param correctly for OAUTH2_DAILY_TOKEN_ISSUE_COUNT")
        void shouldFormatSingleParamCorrectlyForOauth2DailyTokenIssueCount() {
            assertThat(GatewayRedisKey.OAUTH2_DAILY_TOKEN_ISSUE_COUNT.getKey("client-001:20250101"))
                    .isEqualTo("oauth2:daily:issue-count:client-001:20250101");
        }

        @Test
        @DisplayName("should format single param correctly for GATEWAY_API_CONFIG")
        void shouldFormatSingleParamCorrectlyForGatewayApiConfig() {
            assertThat(GatewayRedisKey.GATEWAY_API_CONFIG.getKey("order.read"))
                    .isEqualTo("gateway:api:order.read");
        }

        @Test
        @DisplayName("should format single param correctly for ANOMALY_USER_IPS")
        void shouldFormatSingleParamCorrectlyForAnomalyUserIps() {
            assertThat(GatewayRedisKey.ANOMALY_USER_IPS.getKey("user-999"))
                    .isEqualTo("gateway:anomaly:user:ips:user-999");
        }

        @Test
        @DisplayName("should format single param correctly for DUPLICATE_SUBMIT")
        void shouldFormatSingleParamCorrectlyForDuplicateSubmit() {
            assertThat(GatewayRedisKey.DUPLICATE_SUBMIT.getKey("req-001"))
                    .isEqualTo("platform:gateway:duplicate-submit:req-001");
        }
    }

    @Nested
    @DisplayName("GetKey Multiple Params")
    class GetKeyMultipleParamsTests {

        @Test
        @DisplayName("should format multiple params using String.format")
        void shouldFormatMultipleParamsUsingStringFormat() {
            assertThat(GatewayRedisKey.OAUTH2_CLIENT_CONFIG.getKey("arg1"))
                    .isEqualTo("third-party-client:arg1");
        }

        @Test
        @DisplayName("should handle varargs for OAUTH2_DAILY_TOKEN_ISSUE_COUNT")
        void shouldHandleVarargsForOauth2DailyTokenIssueCount() {
            assertThat(GatewayRedisKey.OAUTH2_DAILY_TOKEN_ISSUE_COUNT.getKey("client-002:20250102"))
                    .isEqualTo("oauth2:daily:issue-count:client-002:20250102");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTests {

        @Test
        @DisplayName("should handle empty string param")
        void shouldHandleEmptyStringParam() {
            assertThat(GatewayRedisKey.OAUTH2_CLIENT_CONFIG.getKey(""))
                    .isEqualTo("third-party-client:");
        }

        @Test
        @DisplayName("should handle special characters in param")
        void shouldHandleSpecialCharactersInParam() {
            assertThat(GatewayRedisKey.OAUTH2_CLIENT_CONFIG.getKey("client:with:colons"))
                    .isEqualTo("third-party-client:client:with:colons");
        }

        @Test
        @DisplayName("should handle colon-separated composed param")
        void shouldHandleColonSeparatedComposedParam() {
            assertThat(GatewayRedisKey.OAUTH2_DAILY_TOKEN_ISSUE_COUNT.getKey("client-001:20250101"))
                    .isEqualTo("oauth2:daily:issue-count:client-001:20250101");
        }
    }

    @Nested
    @DisplayName("All Key Patterns")
    class AllKeyPatternsTests {

        @Test
        @DisplayName("should generate correct keys for OAuth2 related keys")
        void shouldGenerateCorrectKeysForOauth2RelatedKeys() {
            assertThat(GatewayRedisKey.OAUTH2_CLIENT_CONFIG.getKey("c1")).isEqualTo("third-party-client:c1");
            assertThat(GatewayRedisKey.OAUTH2_REFRESH_TOKEN.getKey("t1")).isEqualTo("refresh-token:t1");
            assertThat(GatewayRedisKey.OAUTH2_CLIENT_REFRESH_TOKEN_INDEX.getKey("c1")).isEqualTo("client-refresh-token:c1");
            assertThat(GatewayRedisKey.OAUTH2_ACCESS_TOKEN_BLACKLIST.getKey("at1")).isEqualTo("access-token-blacklist:at1");
            assertThat(GatewayRedisKey.OAUTH2_ACCESS_TOKEN_IP_BIND.getKey("at1")).isEqualTo("access-token-ip:at1");
        }

        @Test
        @DisplayName("should generate correct keys for gateway API keys")
        void shouldGenerateCorrectKeysForGatewayApiKeys() {
            assertThat(GatewayRedisKey.GATEWAY_API_INDEX.getKey()).isEqualTo("gateway:api:index");
            assertThat(GatewayRedisKey.GATEWAY_API_CONFIG.getKey("api1")).isEqualTo("gateway:api:api1");
            assertThat(GatewayRedisKey.GATEWAY_API_SCOPES.getKey("api1")).isEqualTo("gateway:api:scopes:api1");
            assertThat(GatewayRedisKey.GATEWAY_SCOPE_CONFIG.getKey("scope1")).isEqualTo("gateway:scope:scope1");
        }

        @Test
        @DisplayName("should generate correct keys for anomaly detection keys")
        void shouldGenerateCorrectKeysForAnomalyDetectionKeys() {
            assertThat(GatewayRedisKey.ANOMALY_USER_IPS.getKey("u1")).isEqualTo("gateway:anomaly:user:ips:u1");
            assertThat(GatewayRedisKey.ANOMALY_RATELIMIT.getKey("u1")).isEqualTo("gateway:anomaly:ratelimit:u1");
            assertThat(GatewayRedisKey.ANOMALY_FAILURES_USER.getKey("u1")).isEqualTo("gateway:anomaly:failures:user:u1");
            assertThat(GatewayRedisKey.ANOMALY_FAILURES_IP.getKey("ip1")).isEqualTo("gateway:anomaly:failures:ip:ip1");
            assertThat(GatewayRedisKey.ANOMALY_BAN_USER.getKey("u1")).isEqualTo("gateway:anomaly:ban:user:u1");
        }

        @Test
        @DisplayName("should generate correct keys for ECC and duplicate submit keys")
        void shouldGenerateCorrectKeysForEccAndDuplicateSubmitKeys() {
            assertThat(GatewayRedisKey.ECC_CLIENT_PUBLIC_KEY.getKey("c1")).isEqualTo("platform:gateway:ecc:client:publickey:c1");
            assertThat(GatewayRedisKey.ECC_SHARED_KEY.getKey("c1")).isEqualTo("platform:gateway:ecc:sharedkey:c1");
            assertThat(GatewayRedisKey.DUPLICATE_SUBMIT.getKey("r1")).isEqualTo("platform:gateway:duplicate-submit:r1");
        }

        @Test
        @DisplayName("should generate correct keys for visit record and token blacklist")
        void shouldGenerateCorrectKeysForVisitRecordAndTokenBlacklist() {
            assertThat(GatewayRedisKey.VISIT_RECORD.getKey("ip1")).isEqualTo("platform:gateway:visit:ip1");
            assertThat(GatewayRedisKey.TOKEN_BLACKLIST.getKey("t1")).isEqualTo("platform:gateway:token:t1");
            assertThat(GatewayRedisKey.SSO_ONLINE_TOKEN.getKey("k1")).isEqualTo("platform:gateway:online-token:k1");
            assertThat(GatewayRedisKey.SECURITY_PERMANENT_BAN.getKey()).isEqualTo("platform:gateway:security:permanent");
        }
    }
}