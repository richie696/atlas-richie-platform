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
package com.richie.component.oauth.core.support;

import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.ops.KeyOps;
import com.richie.component.cache.ops.StructOps;
import com.richie.component.cache.ops.ValueOps;
import com.richie.component.oauth.core.model.ClientConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultTokenStoreTest {

    @Mock
    private StructOps structOps;
    @Mock
    private KeyOps keyOps;
    @Mock
    private ValueOps valueOps;

    private DefaultTokenStore tokenStore;

    @BeforeEach
    void setUp() {
        tokenStore = new DefaultTokenStore();
    }

    @Test
    void storeRefreshToken_and_loadRefreshToken_roundTrip() {
        String refreshToken = "test-refresh-token-123";
        String clientId = "client-123";
        String ip = "127.0.0.1";
        ClientConfig config = ClientConfig.builder()
                .refreshTokenValidDuration(720)
                .build();

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::struct).thenReturn(structOps);

            tokenStore.storeRefreshToken(refreshToken, clientId, ip, config);

            verify(structOps).set(eq("refresh-token:" + refreshToken), any(Map.class), anyLong());
        }
    }

    @Test
    void loadRefreshToken_whenExists_returnsMap() {
        String refreshToken = "test-refresh-token-123";

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            com.richie.component.cache.ops.FieldOps mockFieldOps = mock(com.richie.component.cache.ops.FieldOps.class);
            cache.when(GlobalCache::field).thenReturn(mockFieldOps);
            when(mockFieldOps.getAll(anyString(), eq(String.class))).thenReturn(Map.of(
                    "client_id", "client-123",
                    "ip", "127.0.0.1"
            ));

            Map<String, String> result = tokenStore.loadRefreshToken(refreshToken);

            assertThat(result).isNotNull();
            assertThat(result).containsEntry("client_id", "client-123");
        }
    }

    @Test
    void removeRefreshToken_callsRemoveCache() {
        String refreshToken = "test-refresh-token-123";

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::key).thenReturn(keyOps);

            tokenStore.removeRefreshToken(refreshToken);

            verify(keyOps).removeCache("refresh-token:" + refreshToken);
        }
    }

    @Test
    void addToBlacklist_and_isBlacklisted_roundTrip() {
        String accessToken = "test-access-token";
        long ttlMillis = 3600000L;

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::value).thenReturn(valueOps);
            cache.when(GlobalCache::key).thenReturn(keyOps);

            tokenStore.addToBlacklist(accessToken, ttlMillis);

            verify(valueOps).set(eq("access-token-blacklist:" + accessToken), eq("1"), eq(ttlMillis));
        }
    }

    @Test
    void isBlacklisted_whenKeyExists_returnsTrue() {
        String accessToken = "blacklisted-token";

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::key).thenReturn(keyOps);
            when(keyOps.hasKey("access-token-blacklist:" + accessToken)).thenReturn(true);

            boolean result = tokenStore.isBlacklisted(accessToken);

            assertThat(result).isTrue();
        }
    }

    @Test
    void isBlacklisted_whenKeyNotExists_returnsFalse() {
        String accessToken = "valid-token";

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::key).thenReturn(keyOps);
            when(keyOps.hasKey("access-token-blacklist:" + accessToken)).thenReturn(false);

            boolean result = tokenStore.isBlacklisted(accessToken);

            assertThat(result).isFalse();
        }
    }

    @Test
    void bindAccessTokenIp_storesBinding() {
        String accessToken = "test-access-token";
        String clientId = "client-123";
        String ip = "127.0.0.1";
        long ttlMillis = 3600000L;

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::struct).thenReturn(structOps);

            tokenStore.bindAccessTokenIp(accessToken, clientId, ip, ttlMillis);

            verify(structOps).set(eq("access-token-ip:" + accessToken), any(Map.class), eq(ttlMillis));
        }
    }

    @Test
    void removeAccessTokenIpBinding_callsRemoveCache() {
        String accessToken = "test-access-token";

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::key).thenReturn(keyOps);

            tokenStore.removeAccessTokenIpBinding(accessToken);

            verify(keyOps).removeCache("access-token-ip:" + accessToken);
        }
    }

    @Test
    void storeClientRefreshTokenIndex_storesIndex() {
        String clientId = "client-123";
        String refreshToken = "test-refresh-token";
        long ttlMillis = 86400000L;

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::value).thenReturn(valueOps);

            tokenStore.storeClientRefreshTokenIndex(clientId, refreshToken, ttlMillis);

            verify(valueOps).set(eq("client-refresh-token:" + clientId), eq(refreshToken), eq(ttlMillis));
        }
    }

    @Test
    void getClientRefreshTokenIndex_whenExists_returnsToken() {
        String clientId = "client-123";

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::value).thenReturn(valueOps);
            when(valueOps.get("client-refresh-token:" + clientId, String.class))
                    .thenReturn("test-refresh-token");

            String result = tokenStore.getClientRefreshTokenIndex(clientId);

            assertThat(result).isEqualTo("test-refresh-token");
        }
    }

    @Test
    void getClientRefreshTokenIndex_whenNotExists_returnsNull() {
        String clientId = "client-123";

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::value).thenReturn(valueOps);
            when(valueOps.get("client-refresh-token:" + clientId, String.class)).thenReturn(null);

            String result = tokenStore.getClientRefreshTokenIndex(clientId);

            assertThat(result).isNull();
        }
    }

    @Test
    void removeClientRefreshTokenIndex_callsRemoveCache() {
        String clientId = "client-123";

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::key).thenReturn(keyOps);

            tokenStore.removeClientRefreshTokenIndex(clientId);

            verify(keyOps).removeCache("client-refresh-token:" + clientId);
        }
    }

    @Test
    void incrementDailyIssueCount_incrementsAndReturns() {
        String clientId = "client-123";
        String date = "20260612";
        long ttlMillis = 86400000L;

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::value).thenReturn(valueOps);
            when(valueOps.increment(anyString(), eq(1L))).thenReturn(5L);

            long result = tokenStore.incrementDailyIssueCount(clientId, date, ttlMillis);

            assertThat(result).isEqualTo(5L);
            verify(valueOps).increment(eq("oauth2:daily:issue-count:client-123:20260612"), eq(1L));
        }
    }

    @Test
    void incrementAnomalyRefreshCount_incrementsAndReturns() {
        String clientId = "client-123";

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::value).thenReturn(valueOps);
            when(valueOps.increment(anyString(), eq(1L))).thenReturn(3L);

            long result = tokenStore.incrementAnomalyRefreshCount(clientId);

            assertThat(result).isEqualTo(3L);
            verify(valueOps).increment(eq("oauth2:anomaly:refresh:count:client-123"), eq(1L));
        }
    }

    @Test
    void incrementAnomalyRateLimit_incrementsAndReturns() {
        String clientId = "client-123";

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::value).thenReturn(valueOps);
            when(valueOps.increment(anyString(), eq(1L))).thenReturn(10L);

            long result = tokenStore.incrementAnomalyRateLimit(clientId);

            assertThat(result).isEqualTo(10L);
            verify(valueOps).increment(eq("oauth2:anomaly:ratelimit:oauth2:client-123"), eq(1L));
        }
    }
}
