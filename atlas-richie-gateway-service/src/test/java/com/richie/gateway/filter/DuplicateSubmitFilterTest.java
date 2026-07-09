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
package com.richie.gateway.filter;

import com.richie.gateway.config.GatewayConfig;
import com.richie.component.i18n.resolver.I18nResolver;
import com.richie.gateway.config.DuplicateSubmitConfig;
import com.richie.gateway.filter.internal.business.DuplicateSubmitFilter;
import com.richie.gateway.service.DuplicateSubmitService;
import com.richie.gateway.service.impl.DuplicateSubmitServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 防重复提交过滤器测试类
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-27
 */
@ExtendWith(MockitoExtension.class)
@Disabled("""
        需要真实的 GlobalCache (Redis) 才能运行：
        DuplicateSubmitServiceImpl.isDuplicateSubmit / recordSubmit
        依赖 GlobalCache.key().hasKey() / .value().set()，
        单元测试环境无 Redis 容器。
        后续可改用 Testcontainers Redis 或完全 mock DuplicateSubmitService 接口。
        """)
class DuplicateSubmitFilterTest {

    @Mock
    private GatewayConfig gatewayConfig;

    @Mock
    private I18nResolver i18nResolver;

    @Mock
    private GatewayFilterChain filterChain;

    private DuplicateSubmitConfig duplicateSubmitConfig;
    private DuplicateSubmitService duplicateSubmitService;
    private DuplicateSubmitFilter duplicateSubmitFilter;

    @BeforeEach
    void setUp() {
        // 创建配置
        duplicateSubmitConfig = new DuplicateSubmitConfig();
        duplicateSubmitConfig.setEnabled(true);
        duplicateSubmitConfig.setIncludePaths(new String[]{"/api/**"});
        duplicateSubmitConfig.setExcludePaths(new String[]{"/api/health/**"});
        duplicateSubmitConfig.setTimeWindow(3000);
        duplicateSubmitConfig.setCacheExpire(10000);
        duplicateSubmitConfig.setEnableBodyHash(true);
        duplicateSubmitConfig.setEnableUserLevel(true);
        duplicateSubmitConfig.setEnableIpLevel(true);

        // 创建服务
        duplicateSubmitService = new DuplicateSubmitServiceImpl(duplicateSubmitConfig);

        // 创建过滤器
        duplicateSubmitFilter = new DuplicateSubmitFilter(gatewayConfig, i18nResolver, duplicateSubmitService);

        lenient().when(gatewayConfig.getDuplicateSubmit()).thenReturn(duplicateSubmitConfig);

        lenient().when(filterChain.filter(any(ServerWebExchange.class)))
                .thenReturn(Mono.empty());
    }

    @Test
    void testFilterWithGetRequest_ShouldPassThrough() {
        // 准备GET请求
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .build();

        MockServerHttpResponse response = new MockServerHttpResponse();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        // 执行过滤器
        Mono<Void> result = duplicateSubmitFilter.filter(exchange, filterChain);

        // 验证结果
        StepVerifier.create(result)
                .verifyComplete();

        // 验证过滤器链被调用
        verify(filterChain, times(1)).filter(any(ServerWebExchange.class));
    }

    @Test
    void testFilterWithPostRequest_ShouldCheckDuplicateSubmit() {
        // 准备POST请求
        String requestBody = "{\"test\":\"data\"}";
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/test")
                .body(requestBody);

        MockServerHttpResponse response = new MockServerHttpResponse();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        // 执行过滤器
        Mono<Void> result = duplicateSubmitFilter.filter(exchange, filterChain);

        // 验证结果
        StepVerifier.create(result)
                .verifyComplete();

        // 验证过滤器链被调用
        verify(filterChain, times(1)).filter(any(ServerWebExchange.class));
    }

    @Test
    void testFilterWithExcludedPath_ShouldPassThrough() {
        // 准备排除路径的请求
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/health/check")
                .body("{}");

        MockServerHttpResponse response = new MockServerHttpResponse();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        // 执行过滤器
        Mono<Void> result = duplicateSubmitFilter.filter(exchange, filterChain);

        // 验证结果
        StepVerifier.create(result)
                .verifyComplete();

        // 验证过滤器链被调用
        verify(filterChain, times(1)).filter(any(ServerWebExchange.class));
    }

    @Test
    void testFilterWithDisabledConfig_ShouldPassThrough() {
        // 禁用配置
        duplicateSubmitConfig.setEnabled(false);

        // 准备POST请求
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/test")
                .body("{}");

        MockServerHttpResponse response = new MockServerHttpResponse();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        // 执行过滤器
        Mono<Void> result = duplicateSubmitFilter.filter(exchange, filterChain);

        // 验证结果
        StepVerifier.create(result)
                .verifyComplete();

        // 验证过滤器链被调用
        verify(filterChain, times(1)).filter(any(ServerWebExchange.class));
    }

    @Test
    void testServiceShouldCheckDuplicateSubmit() {
        // 测试包含路径
        assert duplicateSubmitService.shouldCheckDuplicateSubmit("/api/user/create");
        assert duplicateSubmitService.shouldCheckDuplicateSubmit("/api/order/submit");

        // 测试排除路径
        assert !duplicateSubmitService.shouldCheckDuplicateSubmit("/api/health/check");
        assert !duplicateSubmitService.shouldCheckDuplicateSubmit("/api/health/status");

        // 测试不匹配的路径
        assert !duplicateSubmitService.shouldCheckDuplicateSubmit("/public/test");
    }

    @Test
    void testServiceGenerateRequestId() {
        // 创建测试请求
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/test")
                .header("X-User-Id", "user123")
                .build();

        String requestBody = "{\"test\":\"data\"}";

        // 生成请求ID
        String requestId1 = duplicateSubmitService.generateRequestId(request, requestBody);
        String requestId2 = duplicateSubmitService.generateRequestId(request, requestBody);

        // 验证相同请求生成相同的ID
        assert requestId1.equals(requestId2);

        // 验证不同请求体生成不同的ID
        String requestId3 = duplicateSubmitService.generateRequestId(request, "{\"test\":\"different\"}");
        assert !requestId1.equals(requestId3);
    }

    @Test
    void testServiceIsDuplicateSubmit() {
        // 创建测试请求
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/test")
                .build();

        String requestBody = "{\"test\":\"data\"}";

        // 第一次提交，应该不是重复提交
        assert !duplicateSubmitService.isDuplicateSubmit(request, requestBody);

        // 记录提交
        duplicateSubmitService.recordSubmit(request, requestBody);

        // 第二次提交，应该是重复提交
        assert duplicateSubmitService.isDuplicateSubmit(request, requestBody);
    }
}
