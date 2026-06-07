package com.richie.gateway.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MfaBackupCodeVerifyRequest}.
 */
@DisplayName("MfaBackupCodeVerifyRequest Tests")
class MfaBackupCodeVerifyRequestTest {

    @Nested
    @DisplayName("Default Constructor")
    class DefaultConstructor {

        @Test
        @DisplayName("should have all fields null by default")
        void shouldHaveAllFieldsNullByDefault() {
            MfaBackupCodeVerifyRequest request = new MfaBackupCodeVerifyRequest();
            assertThat(request.getUserId()).isNull();
            assertThat(request.getTenantId()).isNull();
            assertThat(request.getCode()).isNull();
        }
    }

    @Nested
    @DisplayName("Setter and Getter")
    class SetterAndGetter {

        @Test
        @DisplayName("should set and get all fields")
        void shouldSetAndGetAllFields() {
            MfaBackupCodeVerifyRequest request = new MfaBackupCodeVerifyRequest();
            request.setUserId("user-001");
            request.setTenantId("tenant-A");
            request.setCode("BACKUP-CODE-1234");

            assertThat(request.getUserId()).isEqualTo("user-001");
            assertThat(request.getTenantId()).isEqualTo("tenant-A");
            assertThat(request.getCode()).isEqualTo("BACKUP-CODE-1234");
        }
    }

    @Nested
    @DisplayName("Equals and HashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("should be equal when all fields match")
        void shouldBeEqualWhenAllFieldsMatch() {
            MfaBackupCodeVerifyRequest r1 = buildRequest("u", "t", "c");
            MfaBackupCodeVerifyRequest r2 = buildRequest("u", "t", "c");

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when userId differs")
        void shouldNotBeEqualWhenUserIdDiffers() {
            MfaBackupCodeVerifyRequest r1 = buildRequest("u1", "t", "c");
            MfaBackupCodeVerifyRequest r2 = buildRequest("u2", "t", "c");

            assertThat(r1).isNotEqualTo(r2);
        }

        @Test
        @DisplayName("should not be equal when tenantId differs")
        void shouldNotBeEqualWhenTenantIdDiffers() {
            MfaBackupCodeVerifyRequest r1 = buildRequest("u", "t1", "c");
            MfaBackupCodeVerifyRequest r2 = buildRequest("u", "t2", "c");

            assertThat(r1).isNotEqualTo(r2);
        }

        @Test
        @DisplayName("should not be equal when code differs")
        void shouldNotBeEqualWhenCodeDiffers() {
            MfaBackupCodeVerifyRequest r1 = buildRequest("u", "t", "c1");
            MfaBackupCodeVerifyRequest r2 = buildRequest("u", "t", "c2");

            assertThat(r1).isNotEqualTo(r2);
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToStringTests {

        @Test
        @DisplayName("should contain all field names in toString output")
        void shouldContainAllFieldNamesInToStringOutput() {
            MfaBackupCodeVerifyRequest request = buildRequest("u", "t", "c");
            String str = request.toString();

            assertThat(str).contains("userId");
            assertThat(str).contains("tenantId");
            assertThat(str).contains("code");
        }
    }

    private static MfaBackupCodeVerifyRequest buildRequest(String userId, String tenantId, String code) {
        MfaBackupCodeVerifyRequest request = new MfaBackupCodeVerifyRequest();
        request.setUserId(userId);
        request.setTenantId(tenantId);
        request.setCode(code);
        return request;
    }
}
