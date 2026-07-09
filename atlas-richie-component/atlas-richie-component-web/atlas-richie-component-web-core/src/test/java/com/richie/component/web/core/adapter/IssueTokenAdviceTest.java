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
package com.richie.component.web.core.adapter;

import com.richie.component.web.core.config.login.LoginConfig;
import com.richie.component.web.core.config.mvc.CorsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class IssueTokenAdviceTest {

    @Test
    void beforeBodyWrite_skipsWhenCorsDisabled() {
        LoginConfig login = new LoginConfig();
        CorsProperties cors = new CorsProperties();
        cors.setEnabled(false);
        IssueTokenAdvice advice = new IssueTokenAdvice(login, cors);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/login");
        var request = new ServletServerHttpRequest(servletRequest);
        var response = new ServletServerHttpResponse(new MockHttpServletResponse());
        Object body = "payload";

        Object result = advice.beforeBodyWrite(
                body, null, MediaType.APPLICATION_JSON, null, request, response);

        assertThat(result).isSameAs(body);
    }

    @Test
    void supports_alwaysTrue() {
        IssueTokenAdvice advice = new IssueTokenAdvice(new LoginConfig(), new CorsProperties());

        assertThat(advice.supports(null, null)).isTrue();
    }
}
