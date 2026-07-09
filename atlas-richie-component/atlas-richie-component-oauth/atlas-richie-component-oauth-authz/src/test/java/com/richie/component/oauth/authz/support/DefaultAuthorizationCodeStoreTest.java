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
package com.richie.component.oauth.authz.support;

import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.ops.FieldOps;
import com.richie.component.cache.ops.KeyOps;
import com.richie.component.cache.ops.StructOps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DefaultAuthorizationCodeStore 测试")
class DefaultAuthorizationCodeStoreTest {

    @Mock
    private StructOps structOps;
    @Mock
    private FieldOps fieldOps;
    @Mock
    private KeyOps keyOps;

    private DefaultAuthorizationCodeStore store;

    @BeforeEach
    void setUp() {
        store = new DefaultAuthorizationCodeStore();
    }

    @Test
    @DisplayName("storeAuthorizationCode 调用 GlobalCache.struct().set()")
    void storeAuthorizationCode_callsGlobalCacheStructSet() {
        String code = "test-code-123";
        String clientId = "client-abc";
        String redirectUri = "https://example.com/callback";
        String codeChallenge = "challenge-xyz";
        String codeChallengeMethod = "S256";
        List<String> scopes = Arrays.asList("read", "write");
        String userId = "user-001";
        long ttlSeconds = 600;

        try (MockedStatic<GlobalCache> globalCache = mockStatic(GlobalCache.class)) {
            globalCache.when(GlobalCache::struct).thenReturn(structOps);

            store.storeAuthorizationCode(code, clientId, redirectUri, codeChallenge,
                    codeChallengeMethod, scopes, userId, ttlSeconds);

            verify(structOps).set(
                    eq("authz-code:" + code),
                    argThat((Object obj) -> {
                        if (!(obj instanceof Map)) return false;
                        Map<?, ?> map = (Map<?, ?>) obj;
                        return "client-abc".equals(map.get("clientId"))
                                && "https://example.com/callback".equals(map.get("redirectUri"))
                                && "challenge-xyz".equals(map.get("codeChallenge"))
                                && "S256".equals(map.get("codeChallengeMethod"))
                                && "read write".equals(map.get("scopes"))
                                && "user-001".equals(map.get("userId"));
                    }),
                    eq(600_000L)
            );
        }
    }

    @Test
    @DisplayName("storeAuthorizationCode 使用默认 TTL 当 ttlSeconds 为 0")
    void storeAuthorizationCode_usesDefaultTtlWhenZero() {
        String code = "test-code";
        String clientId = "client";
        String redirectUri = "https://example.com/callback";
        String codeChallenge = "challenge";
        String codeChallengeMethod = "S256";
        List<String> scopes = Arrays.asList("read");
        String userId = "user";
        long ttlSeconds = 0;

        try (MockedStatic<GlobalCache> globalCache = mockStatic(GlobalCache.class)) {
            globalCache.when(GlobalCache::struct).thenReturn(structOps);

            store.storeAuthorizationCode(code, clientId, redirectUri, codeChallenge,
                    codeChallengeMethod, scopes, userId, ttlSeconds);

            verify(structOps).set(
                    eq("authz-code:" + code),
                    any(),
                    eq(600_000L)
            );
        }
    }

    @Test
    @DisplayName("loadAuthorizationCode 从 GlobalCache.field().getAll() 返回数据")
    void loadAuthorizationCode_returnsDataFromGlobalCache() {
        String code = "test-code-456";
        Map<String, String> expectedData = new HashMap<>();
        expectedData.put("clientId", "client-xyz");
        expectedData.put("redirectUri", "https://example.com/callback");
        expectedData.put("codeChallenge", "challenge-abc");
        expectedData.put("codeChallengeMethod", "S256");
        expectedData.put("scopes", "read write");
        expectedData.put("userId", "user-123");

        try (MockedStatic<GlobalCache> globalCache = mockStatic(GlobalCache.class)) {
            globalCache.when(GlobalCache::field).thenReturn(fieldOps);
            when(fieldOps.getAll("authz-code:" + code, String.class)).thenReturn(expectedData);

            Map<String, String> result = store.loadAuthorizationCode(code);

            assertThat(result).isEqualTo(expectedData);
            verify(fieldOps).getAll("authz-code:" + code, String.class);
        }
    }

    @Test
    @DisplayName("loadAuthorizationCode 当无数据时返回空 map")
    void loadAuthorizationCode_returnsEmptyMapWhenNoData() {
        String code = "nonexistent-code";

        try (MockedStatic<GlobalCache> globalCache = mockStatic(GlobalCache.class)) {
            globalCache.when(GlobalCache::field).thenReturn(fieldOps);
            when(fieldOps.getAll("authz-code:" + code, String.class)).thenReturn(null);

            Map<String, String> result = store.loadAuthorizationCode(code);

            assertThat(result).isNull();
        }
    }

    @Test
    @DisplayName("consumeAuthorizationCode 调用 GlobalCache.key().removeCache()")
    void consumeAuthorizationCode_callsGlobalCacheKeyRemoveCache() {
        String code = "code-to-consume";

        try (MockedStatic<GlobalCache> globalCache = mockStatic(GlobalCache.class)) {
            globalCache.when(GlobalCache::key).thenReturn(keyOps);

            store.consumeAuthorizationCode(code);

            verify(keyOps).removeCache("authz-code:" + code);
        }
    }
}
