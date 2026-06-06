package com.richie.component.mfa.validation.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MfaValidationResultTest {

    @Test
    void factoryMethods_setExpectedFlags() {
        assertThat(MfaValidationResult.successWithoutMfa())
                .returns(true, MfaValidationResult::isSuccess)
                .returns(false, MfaValidationResult::isMfaRequired);

        assertThat(MfaValidationResult.successWithTrustedDevice())
                .returns(true, MfaValidationResult::isTrustedDevice)
                .returns(false, MfaValidationResult::isMfaRequired);

        assertThat(MfaValidationResult.successWithMfa())
                .returns(true, MfaValidationResult::isSuccess)
                .returns(true, MfaValidationResult::isMfaRequired)
                .returns(true, MfaValidationResult::isMfaBound);

        assertThat(MfaValidationResult.failure("ERR", "msg"))
                .returns(false, MfaValidationResult::isSuccess)
                .returns("ERR", MfaValidationResult::getErrorCode);

        assertThat(MfaValidationResult.mfaRequired(true, 1, 5, 30))
                .returns(true, MfaValidationResult::isMfaRequired)
                .returns(1, MfaValidationResult::getTrustedDeviceCount)
                .returns(5, MfaValidationResult::getMaxTrustedDevices)
                .returns(30, MfaValidationResult::getDefaultTrustDays);
    }
}
