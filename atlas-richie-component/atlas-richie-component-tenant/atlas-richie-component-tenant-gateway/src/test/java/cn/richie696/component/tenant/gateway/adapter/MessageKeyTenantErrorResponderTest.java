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
package cn.richie696.component.tenant.gateway.adapter;

import cn.richie696.contract.exception.I18nMessageKeyException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class MessageKeyTenantErrorResponderTest {

    @Test
    void createsMessageKeyExceptionForGlobalHandler() {
        MessageKeyTenantErrorResponder responder = new MessageKeyTenantErrorResponder();

        StepVerifier.create(responder.unauthorized(
                        MockServerWebExchange.from(MockServerHttpRequest.get("/api/test").build()),
                        "MSG_GATEWAY_TIP_4"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(I18nMessageKeyException.class);
                    I18nMessageKeyException exception = (I18nMessageKeyException) error;
                    assertThat(exception.getMessageKey()).isEqualTo("MSG_GATEWAY_TIP_4");
                    assertThat(exception.getStatusCode()).isEqualTo(401);
                    assertThat(exception.getErrorCode()).isEqualTo("401");
                })
                .verify();
    }
}
