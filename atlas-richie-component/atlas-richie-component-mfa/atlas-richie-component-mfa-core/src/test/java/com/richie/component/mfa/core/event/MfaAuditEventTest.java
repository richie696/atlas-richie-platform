package com.richie.component.mfa.core.event;

import com.richie.component.mfa.core.constant.MfaOperationTypeEnum;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class MfaAuditEventTest {

    @Test
    void builder_populatesFields() {
        OffsetDateTime ts = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        MfaAuditEvent event = MfaAuditEvent.builder(this)
                .tenantId("t1")
                .userId("u1")
                .operationType(MfaOperationTypeEnum.VERIFY)
                .authMethod("TOTP")
                .ipAddress("127.0.0.1")
                .userAgent("JUnit")
                .deviceId("device-1")
                .result("SUCCESS")
                .timestamp(ts)
                .build();

        assertThat(event.getTenantId()).isEqualTo("t1");
        assertThat(event.getUserId()).isEqualTo("u1");
        assertThat(event.getOperationType()).isEqualTo(MfaOperationTypeEnum.VERIFY);
        assertThat(event.getAuthMethod()).isEqualTo("TOTP");
        assertThat(event.getResult()).isEqualTo("SUCCESS");
        assertThat(event.getEventTimestamp()).isEqualTo(ts);
    }

    @Test
    void builder_withErrorCodeErrorMessageAndDuration() {
        MfaAuditEvent event = MfaAuditEvent.builder(this)
                .tenantId("t1")
                .userId("u1")
                .operationType(MfaOperationTypeEnum.VERIFY)
                .authMethod("TOTP")
                .result("FAILED")
                .errorCode("MFA_INVALID")
                .errorMessage("验证码无效")
                .durationMs(150L)
                .build();

        assertThat(event.getErrorCode()).isEqualTo("MFA_INVALID");
        assertThat(event.getErrorMessage()).isEqualTo("验证码无效");
        assertThat(event.getDurationMs()).isEqualTo(150L);
    }
}
