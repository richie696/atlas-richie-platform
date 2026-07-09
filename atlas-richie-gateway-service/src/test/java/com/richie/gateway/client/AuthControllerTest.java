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
package com.richie.gateway.client;

import com.richie.contract.model.ApiResult;
import com.richie.gateway.service.SignatureService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AuthController")
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private SignatureService signatureService;

    @InjectMocks
    private AuthController controller;

    @Nested
    @DisplayName("invalidToken")
    class InvalidTokenTest {

        @Test
        @DisplayName("should call signatureService.invalidToken with given token")
        void shouldCallInvalidToken() {
            when(signatureService.invalidToken("abc123")).thenReturn(ApiResult.success());
            StepVerifier.create(controller.invalidToken("abc123"))
                    .assertNext(result -> assertThat(result.isSuccess()).isTrue())
                    .verifyComplete();
            verify(signatureService).invalidToken("abc123");
        }

        @Test
        @DisplayName("should return ApiResult from service")
        void shouldReturnApiResult() {
            ApiResult<Void> expected = ApiResult.success();
            when(signatureService.invalidToken("tok")).thenReturn(expected);

            StepVerifier.create(controller.invalidToken("tok"))
                    .assertNext(result -> assertThat(result).isSameAs(expected))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("logout")
    class LogoutTest {

        @Test
        @DisplayName("should return success when both tokens are blank")
        void shouldReturnSuccessWhenBothBlank() {
            StepVerifier.create(controller.logout(null, null))
                    .assertNext(result -> assertThat(result.isSuccess()).isTrue())
                    .verifyComplete();
        }

        @Test
        @DisplayName("should return success when accessToken is blank")
        void shouldReturnSuccessWhenAccessTokenBlank() {
            when(signatureService.logout("", "mfa-token")).thenReturn(ApiResult.success());
            StepVerifier.create(controller.logout("", "mfa-token"))
                    .assertNext(result -> assertThat(result.isSuccess()).isTrue())
                    .verifyComplete();
        }

        @Test
        @DisplayName("should return success when mfaToken is blank")
        void shouldReturnSuccessWhenMfaTokenBlank() {
            when(signatureService.logout("access-token", null)).thenReturn(ApiResult.success());
            StepVerifier.create(controller.logout("access-token", null))
                    .assertNext(result -> assertThat(result.isSuccess()).isTrue())
                    .verifyComplete();
        }

        @Test
        @DisplayName("should call signatureService.logout when tokens provided")
        void shouldCallLogoutService() {
            when(signatureService.logout("at", "mt")).thenReturn(ApiResult.success());

            StepVerifier.create(controller.logout("at", "mt"))
                    .assertNext(result -> assertThat(result.isSuccess()).isTrue())
                    .verifyComplete();

            verify(signatureService).logout("at", "mt");
        }
    }
}
