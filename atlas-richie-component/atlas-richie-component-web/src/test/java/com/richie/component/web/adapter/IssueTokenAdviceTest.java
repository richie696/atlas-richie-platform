package com.richie.component.web.adapter;

import com.richie.component.web.config.WebMvcProperties;
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
        WebMvcProperties properties = new WebMvcProperties();
        properties.getCors().setEnable(false);
        IssueTokenAdvice advice = new IssueTokenAdvice(null, properties);
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
        IssueTokenAdvice advice = new IssueTokenAdvice(null, new WebMvcProperties());

        assertThat(advice.supports(null, null)).isTrue();
    }
}
