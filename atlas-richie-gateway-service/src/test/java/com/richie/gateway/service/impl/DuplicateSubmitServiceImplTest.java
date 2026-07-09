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
package com.richie.gateway.service.impl;

import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.GlobalCacheManager;
import com.richie.gateway.config.DuplicateSubmitConfig;
import com.richie.gateway.utils.NetworkUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.util.DigestUtils;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DuplicateSubmitServiceImplTest {

    @Mock
    private DuplicateSubmitConfig config;

    @Mock
    private GlobalCacheManager cacheManager;

    @Mock
    private com.richie.component.cache.ops.ValueOps valueOps;

    @Mock
    private com.richie.component.cache.ops.KeyOps keyOps;

    private DuplicateSubmitServiceImpl service;

    private MockedStatic<GlobalCache> globalCacheMockedStatic;
    private MockedStatic<NetworkUtils> networkUtilsMockedStatic;

    @BeforeEach
    void setUp() throws Exception {
        injectCacheManager();

        globalCacheMockedStatic = mockStatic(GlobalCache.class);
        globalCacheMockedStatic.when(GlobalCache::value).thenReturn(valueOps);
        globalCacheMockedStatic.when(GlobalCache::key).thenReturn(keyOps);

        networkUtilsMockedStatic = mockStatic(NetworkUtils.class);
        networkUtilsMockedStatic.when(() -> NetworkUtils.getIP(any())).thenReturn("192.168.1.100");

        service = new DuplicateSubmitServiceImpl(config);
    }

    private void injectCacheManager() throws Exception {
        Field field = GlobalCache.class.getDeclaredField("DELEGATE");
        field.setAccessible(true);
        AtomicReference<GlobalCacheManager> ref = (AtomicReference<GlobalCacheManager>) field.get(null);
        ref.set(cacheManager);
    }

    @AfterEach
    void tearDown() {
        if (globalCacheMockedStatic != null) globalCacheMockedStatic.close();
        if (networkUtilsMockedStatic != null) networkUtilsMockedStatic.close();
    }

    @Nested
    @DisplayName("shouldCheckDuplicateSubmit 是否检查重复提交")
    class ShouldCheckDuplicateSubmitTests {

        @Test
        @DisplayName("功能禁用时返回 false")
        void shouldCheckDuplicateSubmit_disabled_returnsFalse() {
            when(config.isEnabled()).thenReturn(false);

            boolean result = service.shouldCheckDuplicateSubmit("/api/orders");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("路径匹配排除规则时返回 false")
        void shouldCheckDuplicateSubmit_excludedPath_returnsFalse() {
            when(config.isEnabled()).thenReturn(true);
            doReturn(new String[]{"/actuator/**"}).when(config).getExcludePaths();

            boolean result = service.shouldCheckDuplicateSubmit("/actuator/health");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("路径匹配包含规则时返回 true")
        void shouldCheckDuplicateSubmit_includedPath_returnsTrue() {
            when(config.isEnabled()).thenReturn(true);
            doReturn(new String[]{}).when(config).getExcludePaths();
            doReturn(new String[]{"/api/orders/**"}).when(config).getIncludePaths();

            boolean result = service.shouldCheckDuplicateSubmit("/api/orders/123");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("路径不匹配任何规则时返回 false")
        void shouldCheckDuplicateSubmit_noMatch_returnsFalse() {
            when(config.isEnabled()).thenReturn(true);
            doReturn(new String[]{}).when(config).getExcludePaths();
            doReturn(new String[]{"/api/orders/**"}).when(config).getIncludePaths();

            boolean result = service.shouldCheckDuplicateSubmit("/api/products/123");

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("isDuplicateSubmit 判断是否重复提交")
    class IsDuplicateSubmitTests {

        @Test
        @DisplayName("缓存中存在请求 ID 返回 true")
        void isDuplicateSubmit_cacheHit_returnsTrue() {
            MockServerHttpRequest request = MockServerHttpRequest.post("/api/orders").build();
            when(keyOps.hasKey(anyString())).thenReturn(true);

            boolean result = service.isDuplicateSubmit(request, null);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("缓存中不存在请求 ID 返回 false")
        void isDuplicateSubmit_cacheMiss_returnsFalse() {
            MockServerHttpRequest request = MockServerHttpRequest.post("/api/orders").build();
            when(keyOps.hasKey(anyString())).thenReturn(false);

            boolean result = service.isDuplicateSubmit(request, null);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("recordSubmit 记录提交")
    class RecordSubmitTests {

        @Test
        @DisplayName("正常记录提交到缓存")
        void recordSubmit_normal_setsCache() {
            MockServerHttpRequest request = MockServerHttpRequest.post("/api/orders").build();
            when(config.getCacheExpire()).thenReturn(60000L);

            service.recordSubmit(request, null);

            verify(valueOps).set(anyString(), eq("1"), eq(60000L));
        }
    }

    @Nested
    @DisplayName("generateRequestId 生成请求 ID")
    class GenerateRequestIdTests {

        @Test
        @DisplayName("基础路径和方法生成 MD5")
        void generateRequestId_pathAndMethod_returnsMd5() {
            MockServerHttpRequest request = MockServerHttpRequest.post("/api/orders").build();

            String requestId = service.generateRequestId(request, null);

            assertThat(requestId).hasSize(32);
            assertThat(requestId).matches("[a-f0-9]+");
        }

        @Test
        @DisplayName("启用 IP 级别时包含 IP")
        void generateRequestId_ipLevel_includesIp() {
            MockServerHttpRequest request = MockServerHttpRequest.post("/api/orders").build();
            when(config.isEnableIpLevel()).thenReturn(true);

            String requestId = service.generateRequestId(request, null);

            assertThat(requestId).hasSize(32);
        }

        @Test
        @DisplayName("启用用户级别时包含用户 ID")
        void generateRequestId_userLevel_includesUserId() {
            MockServerHttpRequest request = MockServerHttpRequest.post("/api/orders")
                    .header("X-User-Id", "u-123")
                    .build();
            when(config.isEnableIpLevel()).thenReturn(false);
            when(config.isEnableUserLevel()).thenReturn(true);

            String requestId = service.generateRequestId(request, null);

            assertThat(requestId).hasSize(32);
        }

        @Test
        @DisplayName("启用请求体哈希时包含 body hash")
        void generateRequestId_bodyHash_includesBodyHash() {
            MockServerHttpRequest request = MockServerHttpRequest.post("/api/orders").build();
            when(config.isEnableIpLevel()).thenReturn(false);
            when(config.isEnableUserLevel()).thenReturn(false);
            when(config.isEnableBodyHash()).thenReturn(true);

            String requestId = service.generateRequestId(request, "{\"amount\":100}");

            assertThat(requestId).hasSize(32);
        }

        @Test
        @DisplayName("禁用所有选项时仅包含路径和方法")
        void generateRequestId_allDisabled_onlyPathAndMethod() {
            MockServerHttpRequest request = MockServerHttpRequest.post("/api/orders").build();
            when(config.isEnableIpLevel()).thenReturn(false);
            when(config.isEnableUserLevel()).thenReturn(false);
            when(config.isEnableBodyHash()).thenReturn(false);

            String requestId = service.generateRequestId(request, null);

            assertThat(requestId).hasSize(32);
        }

        @Test
        @DisplayName("不同请求体生成不同 requestId")
        void generateRequestId_differentBodies_differentIds() {
            MockServerHttpRequest request = MockServerHttpRequest.post("/api/orders").build();
            when(config.isEnableIpLevel()).thenReturn(false);
            when(config.isEnableUserLevel()).thenReturn(false);
            when(config.isEnableBodyHash()).thenReturn(true);

            String id1 = service.generateRequestId(request, "{\"amount\":100}");
            String id2 = service.generateRequestId(request, "{\"amount\":200}");

            assertThat(id1).isNotEqualTo(id2);
        }
    }
}
