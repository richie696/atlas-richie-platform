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
