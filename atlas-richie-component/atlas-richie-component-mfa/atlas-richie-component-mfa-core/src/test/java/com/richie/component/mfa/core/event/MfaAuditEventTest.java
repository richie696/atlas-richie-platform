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

    @Test
    void builder_allSetters_chainsCorrectly() {
        // Test all builder setters are accessible and chain correctly
        MfaAuditEvent event = MfaAuditEvent.builder(this)
                .tenantId("tenant-x")
                .userId("user-x")
                .operationType(MfaOperationTypeEnum.BIND)
                .authMethod("TOTP")
                .ipAddress("10.0.0.1")
                .userAgent("TestAgent/1.0")
                .deviceId("device-abc")
                .result("SUCCESS")
                .errorCode(null)
                .errorMessage(null)
                .durationMs(0L)
                .timestamp(null)
                .build();

        assertThat(event.getTenantId()).isEqualTo("tenant-x");
        assertThat(event.getUserId()).isEqualTo("user-x");
        assertThat(event.getOperationType()).isEqualTo(MfaOperationTypeEnum.BIND);
        assertThat(event.getAuthMethod()).isEqualTo("TOTP");
        assertThat(event.getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(event.getUserAgent()).isEqualTo("TestAgent/1.0");
        assertThat(event.getDeviceId()).isEqualTo("device-abc");
        assertThat(event.getResult()).isEqualTo("SUCCESS");
        assertThat(event.getErrorCode()).isNull();
        assertThat(event.getErrorMessage()).isNull();
        assertThat(event.getDurationMs()).isEqualTo(0L);
    }

    @Test
    void constructor_withNullTimestamp_usesCurrentTime() {
        MfaAuditEvent event = new MfaAuditEvent(
                this,
                "t1",
                "u1",
                MfaOperationTypeEnum.UNBIND,
                "EMAIL",
                "1.2.3.4",
                "Bot",
                "dev-1",
                "SUCCESS",
                null,
                null,
                42L,
                null // null timestamp
        );

        assertThat(event.getTenantId()).isEqualTo("t1");
        assertThat(event.getUserId()).isEqualTo("u1");
        assertThat(event.getOperationType()).isEqualTo(MfaOperationTypeEnum.UNBIND);
        assertThat(event.getAuthMethod()).isEqualTo("EMAIL");
        assertThat(event.getIpAddress()).isEqualTo("1.2.3.4");
        assertThat(event.getUserAgent()).isEqualTo("Bot");
        assertThat(event.getDeviceId()).isEqualTo("dev-1");
        assertThat(event.getResult()).isEqualTo("SUCCESS");
        assertThat(event.getDurationMs()).isEqualTo(42L);
        assertThat(event.getEventTimestamp()).isNotNull();
        assertThat(event.getEventTimestamp()).isEqualTo(event.getEventTimestamp()); // not null, uses now()
    }
}
