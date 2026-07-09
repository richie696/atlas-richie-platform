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
package com.richie.component.web.core.business;

import com.richie.component.web.core.config.business.BusinessIntegrationProperties;
import com.richie.component.web.core.spi.support.DefaultWebInterceptorChain;
import com.richie.component.web.core.spi.support.MutableWebRequestContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiVersionInterceptorTest {

    private final BusinessIntegrationProperties.ApiVersion config = new BusinessIntegrationProperties.ApiVersion();
    private final ApiVersionInterceptor interceptor = new ApiVersionInterceptor(config);

    @Test
    void headerPresent_setsAttribute() throws Exception {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users")
                .header("X-Api-Version", "v2")
                .build();
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        assertThat((String) ctx.attribute("apiVersion")).isEqualTo("v2");
    }

    @Test
    void headerMissing_usesDefaultVersion() throws Exception {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users")
                .build();
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        assertThat((String) ctx.attribute("apiVersion")).isEqualTo("default");
    }

    @Test
    void blankHeader_usesDefaultVersion() throws Exception {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users")
                .header("X-Api-Version", "   ")
                .build();
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        assertThat((String) ctx.attribute("apiVersion")).isEqualTo("default");
    }

    @Test
    void customAttributeKey_isHonored() throws Exception {
        BusinessIntegrationProperties.ApiVersion custom = new BusinessIntegrationProperties.ApiVersion();
        custom.setAttributeKey("version-tag");
        ApiVersionInterceptor inter = new ApiVersionInterceptor(custom);
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users")
                .header("X-Api-Version", "v3")
                .build();
        inter.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        assertThat((String) ctx.attribute("version-tag")).isEqualTo("v3");
    }

    @Test
    void customHeaderName_isHonored() throws Exception {
        BusinessIntegrationProperties.ApiVersion custom = new BusinessIntegrationProperties.ApiVersion();
        custom.setHeaderName("Accept-Version");
        ApiVersionInterceptor inter = new ApiVersionInterceptor(custom);
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users")
                .header("Accept-Version", "v4")
                .build();
        inter.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        assertThat((String) ctx.attribute("apiVersion")).isEqualTo("v4");
    }

    @Test
    void chainProceed_alwaysCalled() throws Exception {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users")
                .build();
        boolean[] proceeded = {false};
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of((c, ch) -> proceeded[0] = true)));
        assertThat(proceeded[0]).isTrue();
        assertThat(ctx.isShortCircuited()).isFalse();
    }

    @Test
    void getOrder_is280() {
        assertThat(interceptor.getOrder()).isEqualTo(280);
    }
}