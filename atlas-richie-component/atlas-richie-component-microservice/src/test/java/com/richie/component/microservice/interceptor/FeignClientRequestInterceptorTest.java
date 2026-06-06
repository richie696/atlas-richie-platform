package com.richie.component.microservice.interceptor;

import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class FeignClientRequestInterceptorTest {

    private final FeignClientRequestInterceptor interceptor = new FeignClientRequestInterceptor();

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void apply_noRequestContext_doesNotAddHeaders() {
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers()).isEmpty();
    }

    @Test
    void apply_forwardsNonIgnoredHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Custom-Token", "abc");
        request.addHeader("Host", "localhost");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers()).containsKey("X-Custom-Token");
        assertThat(template.headers()).doesNotContainKey("Host");
    }

    @Test
    void init_doesNotThrow() {
        interceptor.init();
    }
}
