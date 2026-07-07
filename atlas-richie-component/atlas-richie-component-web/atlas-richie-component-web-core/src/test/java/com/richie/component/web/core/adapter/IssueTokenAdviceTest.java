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
