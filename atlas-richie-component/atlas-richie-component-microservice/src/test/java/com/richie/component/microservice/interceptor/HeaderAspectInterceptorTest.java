package com.richie.component.microservice.interceptor;

import com.richie.context.common.api.HeaderContextHolder;
import com.richie.contract.constant.GlobalConstants;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class HeaderAspectInterceptorTest {

    private final HeaderAspectInterceptor interceptor = new HeaderAspectInterceptor();

    @AfterEach
    void tearDown() {
        HeaderContextHolder.removeContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void postHandle_writesHeadersFromContext() {
        HeaderContextHolder.setHeader(GlobalConstants.X_RD_REQUEST_LANGUAGE, "en-US");
        HeaderContextHolder.setHeader(GlobalConstants.X_TENANT_CODE_TOKEN, "tenant-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.postHandle(response);

        assertThat(response.getHeader(GlobalConstants.X_RD_REQUEST_LANGUAGE)).isEqualTo("en-US");
        assertThat(response.getHeader(GlobalConstants.X_TENANT_CODE_TOKEN)).isEqualTo("tenant-1");
        assertThat(HeaderContextHolder.getHeader(GlobalConstants.X_RD_REQUEST_LANGUAGE)).isNull();
    }

    @Test
    void postHandle_writesAllSupportedHeaders() {
        HeaderContextHolder.setHeader(GlobalConstants.X_TIME_FORMAT_PATTERN, "yyyy-MM-dd");
        HeaderContextHolder.setHeader(GlobalConstants.X_CURRENCY_FORMAT_PATTERN, "#,##0.00");
        HeaderContextHolder.setHeader(GlobalConstants.X_RD_REQUEST_TIMEZONE, "Asia/Shanghai");
        HeaderContextHolder.setHeader(GlobalConstants.X_RD_REQUEST_SHOP_CODE, "shop-1");
        HeaderContextHolder.setHeader(GlobalConstants.X_RD_REQUEST_EXTRA, "extra");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.postHandle(response);

        assertThat(response.getHeader(GlobalConstants.X_TIME_FORMAT_PATTERN)).isEqualTo("yyyy-MM-dd");
        assertThat(response.getHeader(GlobalConstants.X_CURRENCY_FORMAT_PATTERN)).isEqualTo("#,##0.00");
        assertThat(response.getHeader(GlobalConstants.X_RD_REQUEST_TIMEZONE)).isEqualTo("Asia/Shanghai");
        assertThat(response.getHeader(GlobalConstants.X_RD_REQUEST_SHOP_CODE)).isEqualTo("shop-1");
        assertThat(response.getHeader(GlobalConstants.X_RD_REQUEST_EXTRA)).isEqualTo("extra");
    }
}
