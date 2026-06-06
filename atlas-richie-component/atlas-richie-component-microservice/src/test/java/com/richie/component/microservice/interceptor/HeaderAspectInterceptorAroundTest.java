package com.richie.component.microservice.interceptor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HeaderAspectInterceptorAroundTest {

    private final HeaderAspectInterceptor interceptor = new HeaderAspectInterceptor();

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void recordLog_proceedsAndCleansContext() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Custom", "value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        var joinPoint = mock(org.aspectj.lang.ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = interceptor.recordLog(joinPoint);

        assertThat(result).isEqualTo("ok");
    }
}
