package com.richie.component.oauth.core;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.ops.LockOps;
import com.richie.component.cache.redis.manage.CacheLock;
import com.richie.component.oauth.core.config.OAuth2Properties;
import com.richie.component.oauth.core.model.ClientConfig;
import com.richie.component.oauth.core.model.TokenIntrospection;
import com.richie.component.oauth.core.model.TokenResponse;
import com.richie.component.oauth.core.spi.TokenStore;
import com.richie.context.utils.spring.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TokenEndpointTest {

    @Mock
    private TokenStore tokenStore;
    @Mock
    private ClientRegistry clientRegistry;
    @Mock
    private OAuth2Properties properties;

    private TokenEndpoint tokenEndpoint;

    @BeforeEach
    void setUp() {
        tokenEndpoint = new TokenEndpoint(tokenStore, clientRegistry, properties);
    }

    @Test
    void generateToken_whenValidClient_returnsTokenResponse() {
        String clientId = "client-123";
        String clientSecret = "secret-abc";
        String clientIp = "127.0.0.1";
        when(clientRegistry.verifyClientSecret(clientId, clientSecret)).thenReturn(true);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.ENABLED))).thenReturn(true);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.CLIENT_ID))).thenReturn(clientId);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.CLIENT_SECRET))).thenReturn(clientSecret);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.CLIENT_NAME))).thenReturn("Test Client");
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.SCOPES))).thenReturn(List.of("read", "write"));
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.TOKEN_VALID_DURATION))).thenReturn(2);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.REFRESH_TOKEN_VALID_DURATION))).thenReturn(720);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.RATE_LIMIT))).thenReturn(100);
        when(properties.isEnableDailyIssueLimit()).thenReturn(false);
        when(properties.isRevokePreviousTokensOnIssue()).thenReturn(false);
        when(properties.getTokenSecret()).thenReturn("test-secret-key-32chars-long!!!!");

        TokenResponse response = tokenEndpoint.generateToken(clientId, clientSecret, clientIp);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(7200L);
        assertThat(response.getRefreshToken()).isNotBlank();
        verify(tokenStore).storeRefreshToken(anyString(), eq(clientId), eq(clientIp), any(ClientConfig.class));
        verify(tokenStore).bindAccessTokenIp(anyString(), eq(clientId), eq(clientIp), anyLong());
    }

    @Test
    void generateToken_whenInvalidClientSecret_throwsException() {
        String clientId = "client-123";
        String clientSecret = "wrong-secret";
        when(clientRegistry.verifyClientSecret(clientId, clientSecret)).thenReturn(false);

        assertThatThrownBy(() -> tokenEndpoint.generateToken(clientId, clientSecret, "127.0.0.1"))
                .isInstanceOf(com.richie.contract.exception.BusinessException.class);

        verify(tokenStore, never()).storeRefreshToken(anyString(), anyString(), anyString(), any());
    }

    @Test
    void generateToken_whenClientDisabled_throwsException() {
        String clientId = "client-123";
        String clientSecret = "secret-abc";
        when(clientRegistry.verifyClientSecret(clientId, clientSecret)).thenReturn(true);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.ENABLED))).thenReturn(false);

        assertThatThrownBy(() -> tokenEndpoint.generateToken(clientId, clientSecret, "127.0.0.1"))
                .isInstanceOf(com.richie.contract.exception.BusinessException.class);
    }

    @Test
    void generateToken_whenClientNotExists_throwsException() {
        String clientId = "nonexistent-client";
        String clientSecret = "secret-abc";
        when(clientRegistry.verifyClientSecret(clientId, clientSecret)).thenReturn(true);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.ENABLED))).thenReturn(null);

        assertThatThrownBy(() -> tokenEndpoint.generateToken(clientId, clientSecret, "127.0.0.1"))
                .isInstanceOf(com.richie.contract.exception.BusinessException.class);
    }

    @Test
    void revokeToken_whenAccessToken_addsToBlacklist() {
        String accessToken = "valid.jwt.token";
        String tokenSecret = "test-secret-key-32chars-long!!!!";

        try (MockedStatic<JwtUtils> jwtUtils = mockStatic(JwtUtils.class)) {
            jwtUtils.when(() -> JwtUtils.verify(accessToken, tokenSecret)).thenReturn(true);
            jwtUtils.when(() -> JwtUtils.getExpiredTime(accessToken))
                    .thenReturn(new java.util.Date(System.currentTimeMillis() + 3600000));
            when(properties.getTokenSecret()).thenReturn(tokenSecret);

            tokenEndpoint.revokeToken(accessToken, "access_token");

            verify(tokenStore).addToBlacklist(eq(accessToken), anyLong());
            verify(tokenStore).removeAccessTokenIpBinding(accessToken);
        }
    }

    @Test
    void revokeToken_whenRefreshToken_removesFromStore() {
        String refreshToken = "refresh-token-123";

        tokenEndpoint.revokeToken(refreshToken, "refresh_token");

        verify(tokenStore).removeRefreshToken(refreshToken);
        verify(tokenStore, never()).addToBlacklist(anyString(), anyLong());
    }

    @Test
    void revokeToken_whenEmptyToken_doesNothing() {
        tokenEndpoint.revokeToken("", "access_token");
        tokenEndpoint.revokeToken(null, "access_token");

        verify(tokenStore, never()).addToBlacklist(anyString(), anyLong());
        verify(tokenStore, never()).removeRefreshToken(anyString());
    }

    @Test
    void introspectToken_whenActiveToken_returnsActiveIntrospection() {
        String accessToken = "valid.jwt.token";
        String clientId = "client-123";
        String tokenSecret = "test-secret-key-32chars-long!!!!";

        try (MockedStatic<JwtUtils> jwtUtils = mockStatic(JwtUtils.class)) {
            jwtUtils.when(() -> JwtUtils.verify(accessToken, tokenSecret)).thenReturn(true);
            jwtUtils.when(() -> JwtUtils.getExpiredTime(accessToken))
                    .thenReturn(new java.util.Date(System.currentTimeMillis() + 3600000));
            jwtUtils.when(() -> JwtUtils.getArgument(accessToken, "clientId")).thenReturn(clientId);

            when(properties.getTokenSecret()).thenReturn(tokenSecret);
            when(tokenStore.isBlacklisted(accessToken)).thenReturn(false);
            when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.ENABLED), eq(ClientConfig.Field.SCOPES)))
                    .thenReturn(Map.of(
                            ClientConfig.Field.ENABLED, true,
                            ClientConfig.Field.SCOPES, List.of("read", "write")
                    ));

            TokenIntrospection introspection = tokenEndpoint.introspectToken(accessToken);

            assertThat(introspection).isNotNull();
            assertThat(introspection.isActive()).isTrue();
            assertThat(introspection.getClientId()).isEqualTo(clientId);
            assertThat(introspection.getTokenType()).isEqualTo("Bearer");
        }
    }

    @Test
    void introspectToken_whenBlankToken_returnsInactive() {
        TokenIntrospection introspection = tokenEndpoint.introspectToken("");
        assertThat(introspection.isActive()).isFalse();

        introspection = tokenEndpoint.introspectToken(null);
        assertThat(introspection.isActive()).isFalse();
    }

    @Test
    void introspectToken_whenBlacklistedToken_returnsInactive() {
        String accessToken = "blacklisted.jwt.token";
        String clientId = "client-123";
        String tokenSecret = "test-secret-key-32chars-long!!!!";

        try (MockedStatic<JwtUtils> jwtUtils = mockStatic(JwtUtils.class)) {
            jwtUtils.when(() -> JwtUtils.verify(accessToken, tokenSecret)).thenReturn(true);
            jwtUtils.when(() -> JwtUtils.getExpiredTime(accessToken))
                    .thenReturn(new java.util.Date(System.currentTimeMillis() + 3600000));
            jwtUtils.when(() -> JwtUtils.getArgument(accessToken, "clientId")).thenReturn(clientId);

            when(properties.getTokenSecret()).thenReturn(tokenSecret);
            when(tokenStore.isBlacklisted(accessToken)).thenReturn(true);

            TokenIntrospection introspection = tokenEndpoint.introspectToken(accessToken);

            assertThat(introspection.isActive()).isFalse();
        }
    }

    @Test
    void verifyAccessToken_whenValidToken_returnsClientConfig() {
        String accessToken = "valid.jwt.token";
        String clientId = "client-123";
        String tokenSecret = "test-secret-key-32chars-long!!!!";

        try (MockedStatic<JwtUtils> jwtUtils = mockStatic(JwtUtils.class)) {
            jwtUtils.when(() -> JwtUtils.verify(accessToken, tokenSecret)).thenReturn(true);
            jwtUtils.when(() -> JwtUtils.getExpiredTime(accessToken))
                    .thenReturn(new java.util.Date(System.currentTimeMillis() + 3600000));
            jwtUtils.when(() -> JwtUtils.getArgument(accessToken, "clientId")).thenReturn(clientId);

            when(properties.getTokenSecret()).thenReturn(tokenSecret);
            when(tokenStore.isBlacklisted(accessToken)).thenReturn(false);
            when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.ENABLED), eq(ClientConfig.Field.SCOPES)))
                    .thenReturn(Map.of(
                            ClientConfig.Field.ENABLED, true,
                            ClientConfig.Field.SCOPES, List.of("read")
                    ));

            ClientConfig config = tokenEndpoint.verifyAccessToken(accessToken);

            assertThat(config).isNotNull();
            assertThat(config.getClientId()).isEqualTo(clientId);
            assertThat(config.getEnabled()).isTrue();
        }
    }

    @Test
    void verifyAccessToken_whenInvalidToken_returnsNull() {
        String accessToken = "invalid.jwt.token";
        String tokenSecret = "test-secret-key-32chars-long!!!!";

        try (MockedStatic<JwtUtils> jwtUtils = mockStatic(JwtUtils.class)) {
            jwtUtils.when(() -> JwtUtils.verify(accessToken, tokenSecret)).thenReturn(false);

            when(properties.getTokenSecret()).thenReturn(tokenSecret);

            ClientConfig config = tokenEndpoint.verifyAccessToken(accessToken);

            assertThat(config).isNull();
        }
    }

    @Test
    void getIpWhitelist_whenValidToken_returnsWhitelist() {
        String accessToken = "valid.jwt.token";
        String clientId = "client-123";
        String tokenSecret = "test-secret-key-32chars-long!!!!";

        try (MockedStatic<JwtUtils> jwtUtils = mockStatic(JwtUtils.class)) {
            jwtUtils.when(() -> JwtUtils.verify(accessToken, tokenSecret)).thenReturn(true);
            jwtUtils.when(() -> JwtUtils.getExpiredTime(accessToken))
                    .thenReturn(new java.util.Date(System.currentTimeMillis() + 3600000));
            jwtUtils.when(() -> JwtUtils.getArgument(accessToken, "clientId")).thenReturn(clientId);

            when(properties.getTokenSecret()).thenReturn(tokenSecret);
            when(tokenStore.isBlacklisted(accessToken)).thenReturn(false);
            when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.ENABLED), eq(ClientConfig.Field.IP_WHITELIST)))
                    .thenReturn(Map.of(
                            ClientConfig.Field.ENABLED, true,
                            ClientConfig.Field.IP_WHITELIST, List.of("127.0.0.1", "192.168.1.1")
                    ));

            List<String> whitelist = tokenEndpoint.getIpWhitelist(accessToken);

            assertThat(whitelist).isNotNull();
            assertThat(whitelist).containsExactly("127.0.0.1", "192.168.1.1");
        }
    }

    @Test
    void getIpWhitelist_whenNoWhitelist_returnsEmptyList() {
        String accessToken = "valid.jwt.token";
        String clientId = "client-123";
        String tokenSecret = "test-secret-key-32chars-long!!!!";

        try (MockedStatic<JwtUtils> jwtUtils = mockStatic(JwtUtils.class)) {
            jwtUtils.when(() -> JwtUtils.verify(accessToken, tokenSecret)).thenReturn(true);
            jwtUtils.when(() -> JwtUtils.getExpiredTime(accessToken))
                    .thenReturn(new java.util.Date(System.currentTimeMillis() + 3600000));
            jwtUtils.when(() -> JwtUtils.getArgument(accessToken, "clientId")).thenReturn(clientId);

            when(properties.getTokenSecret()).thenReturn(tokenSecret);
            when(tokenStore.isBlacklisted(accessToken)).thenReturn(false);
            when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.ENABLED), eq(ClientConfig.Field.IP_WHITELIST)))
                    .thenReturn(Map.of(
                            ClientConfig.Field.ENABLED, true,
                            ClientConfig.Field.IP_WHITELIST, Collections.emptyList()
                    ));

            List<String> whitelist = tokenEndpoint.getIpWhitelist(accessToken);

            assertThat(whitelist).isEmpty();
        }
    }

    // ==================== refreshToken Tests ====================

    @Test
    void refreshToken_success() {
        String refreshToken = "valid-refresh-token";
        String clientIp = "127.0.0.1";
        String clientId = "client-123";

        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            LockOps lockOps = mock(LockOps.class);
            CacheLock cacheLock = mock(CacheLock.class);
            globalCacheMock.when(GlobalCache::lock).thenReturn(lockOps);
            when(lockOps.optimisticWithRenewal(anyString(), anyLong())).thenReturn(cacheLock);
            when(cacheLock.isSuccess()).thenReturn(true);

            when(tokenStore.loadRefreshToken(refreshToken)).thenReturn(Map.of(
                    "ip", clientIp,
                    "clientId", clientId
            ));
            when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.ENABLED))).thenReturn(true);
            when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.CLIENT_ID))).thenReturn(clientId);
            when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.CLIENT_SECRET))).thenReturn("secret");
            when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.CLIENT_NAME))).thenReturn("Test");
            when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.SCOPES))).thenReturn(List.of("read"));
            when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.TOKEN_VALID_DURATION))).thenReturn(2);
            when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.REFRESH_TOKEN_VALID_DURATION))).thenReturn(720);
            when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.RATE_LIMIT))).thenReturn(100);
            when(properties.getTokenSecret()).thenReturn("test-secret-key-32chars-long!!!!");

            TokenResponse response = tokenEndpoint.refreshToken(refreshToken, clientIp);

            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isNotBlank();
            assertThat(response.getRefreshToken()).isNotBlank();
            verify(tokenStore).removeRefreshToken(refreshToken);
            verify(tokenStore).storeRefreshToken(anyString(), eq(clientId), eq(clientIp), any(ClientConfig.class));
        }
    }

    @Test
    void refreshToken_blankToken_throwsException() {
        assertThatThrownBy(() -> tokenEndpoint.refreshToken(null, "127.0.0.1"))
                .isInstanceOf(com.richie.contract.exception.BusinessException.class)
                .hasMessageContaining("刷新令牌不能为空");

        assertThatThrownBy(() -> tokenEndpoint.refreshToken("", "127.0.0.1"))
                .isInstanceOf(com.richie.contract.exception.BusinessException.class)
                .hasMessageContaining("刷新令牌不能为空");
    }

    @Test
    void refreshToken_lockContention_throwsException() {
        String refreshToken = "valid-refresh-token";

        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            LockOps lockOps = mock(LockOps.class);
            CacheLock cacheLock = mock(CacheLock.class);
            globalCacheMock.when(GlobalCache::lock).thenReturn(lockOps);
            when(lockOps.optimisticWithRenewal(anyString(), anyLong())).thenReturn(cacheLock);
            when(cacheLock.isSuccess()).thenReturn(false);

            assertThatThrownBy(() -> tokenEndpoint.refreshToken(refreshToken, "127.0.0.1"))
                    .isInstanceOf(com.richie.contract.exception.BusinessException.class)
                    .hasMessageContaining("刷新令牌正在处理中");
        }
    }

    @Test
    void refreshToken_invalidToken_throwsException() {
        String refreshToken = "invalid-refresh-token";

        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            LockOps lockOps = mock(LockOps.class);
            CacheLock cacheLock = mock(CacheLock.class);
            globalCacheMock.when(GlobalCache::lock).thenReturn(lockOps);
            when(lockOps.optimisticWithRenewal(anyString(), anyLong())).thenReturn(cacheLock);
            when(cacheLock.isSuccess()).thenReturn(true);
            when(tokenStore.loadRefreshToken(refreshToken)).thenReturn(null);

            assertThatThrownBy(() -> tokenEndpoint.refreshToken(refreshToken, "127.0.0.1"))
                    .isInstanceOf(com.richie.contract.exception.BusinessException.class)
                    .hasMessageContaining("刷新令牌无效或已使用");
        }
    }

    @Test
    void refreshToken_emptyTokenData_throwsException() {
        String refreshToken = "empty-refresh-token";

        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            LockOps lockOps = mock(LockOps.class);
            CacheLock cacheLock = mock(CacheLock.class);
            globalCacheMock.when(GlobalCache::lock).thenReturn(lockOps);
            when(lockOps.optimisticWithRenewal(anyString(), anyLong())).thenReturn(cacheLock);
            when(cacheLock.isSuccess()).thenReturn(true);
            when(tokenStore.loadRefreshToken(refreshToken)).thenReturn(Map.of());

            assertThatThrownBy(() -> tokenEndpoint.refreshToken(refreshToken, "127.0.0.1"))
                    .isInstanceOf(com.richie.contract.exception.BusinessException.class)
                    .hasMessageContaining("刷新令牌无效或已使用");
        }
    }

    @Test
    void refreshToken_ipMismatch_throwsException() {
        String refreshToken = "valid-refresh-token";
        String boundIp = "10.0.0.1";
        String currentIp = "127.0.0.1";

        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            LockOps lockOps = mock(LockOps.class);
            CacheLock cacheLock = mock(CacheLock.class);
            globalCacheMock.when(GlobalCache::lock).thenReturn(lockOps);
            when(lockOps.optimisticWithRenewal(anyString(), anyLong())).thenReturn(cacheLock);
            when(cacheLock.isSuccess()).thenReturn(true);
            when(tokenStore.loadRefreshToken(refreshToken)).thenReturn(Map.of(
                    "ip", boundIp,
                    "clientId", "client-123"
            ));

            assertThatThrownBy(() -> tokenEndpoint.refreshToken(refreshToken, currentIp))
                    .isInstanceOf(com.richie.contract.exception.BusinessException.class)
                    .hasMessageContaining("刷新令牌绑定 IP 不匹配");
        }
    }

    @Test
    void refreshToken_noIpBinding_success() {
        String refreshToken = "valid-refresh-token";
        String currentIp = "127.0.0.1";
        String clientId = "client-123";

        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            LockOps lockOps = mock(LockOps.class);
            CacheLock cacheLock = mock(CacheLock.class);
            globalCacheMock.when(GlobalCache::lock).thenReturn(lockOps);
            when(lockOps.optimisticWithRenewal(anyString(), anyLong())).thenReturn(cacheLock);
            when(cacheLock.isSuccess()).thenReturn(true);
            when(tokenStore.loadRefreshToken(refreshToken)).thenReturn(Map.of(
                    "ip", "",
                    "clientId", clientId
            ));
            when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.ENABLED))).thenReturn(true);
            when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.CLIENT_ID))).thenReturn(clientId);
            when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.CLIENT_SECRET))).thenReturn("secret");
            when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.CLIENT_NAME))).thenReturn("Test");
            when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.SCOPES))).thenReturn(null);
            when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.TOKEN_VALID_DURATION))).thenReturn(2);
            when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.REFRESH_TOKEN_VALID_DURATION))).thenReturn(720);
            when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.RATE_LIMIT))).thenReturn(100);
            when(properties.getTokenSecret()).thenReturn("test-secret-key-32chars-long!!!!");

            TokenResponse response = tokenEndpoint.refreshToken(refreshToken, currentIp);

            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isNotBlank();
        }
    }

    @Test
    void refreshToken_disabledClient_throwsException() {
        String refreshToken = "valid-refresh-token";
        String clientIp = "127.0.0.1";
        String clientId = "client-123";

        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            LockOps lockOps = mock(LockOps.class);
            CacheLock cacheLock = mock(CacheLock.class);
            globalCacheMock.when(GlobalCache::lock).thenReturn(lockOps);
            when(lockOps.optimisticWithRenewal(anyString(), anyLong())).thenReturn(cacheLock);
            when(cacheLock.isSuccess()).thenReturn(true);
            when(tokenStore.loadRefreshToken(refreshToken)).thenReturn(Map.of(
                    "ip", clientIp,
                    "clientId", clientId
            ));
            when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.ENABLED))).thenReturn(false);

            assertThatThrownBy(() -> tokenEndpoint.refreshToken(refreshToken, clientIp))
                    .isInstanceOf(com.richie.contract.exception.BusinessException.class)
                    .hasMessageContaining("客户端不存在或已被禁用");
        }
    }

    // ==================== generateToken Daily Issue Limit Tests ====================

    @Test
    void generateToken_withDailyIssueLimit() {
        String clientId = "client-123";
        String clientSecret = "secret-abc";
        String clientIp = "127.0.0.1";

        when(clientRegistry.verifyClientSecret(clientId, clientSecret)).thenReturn(true);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.ENABLED))).thenReturn(true);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.CLIENT_ID))).thenReturn(clientId);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.CLIENT_SECRET))).thenReturn(clientSecret);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.CLIENT_NAME))).thenReturn("Test Client");
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.SCOPES))).thenReturn(List.of("read"));
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.TOKEN_VALID_DURATION))).thenReturn(2);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.REFRESH_TOKEN_VALID_DURATION))).thenReturn(720);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.RATE_LIMIT))).thenReturn(100);
        when(properties.isEnableDailyIssueLimit()).thenReturn(true);
        when(properties.isRevokePreviousTokensOnIssue()).thenReturn(false);
        when(properties.getTokenSecret()).thenReturn("test-secret-key-32chars-long!!!!");
        when(tokenStore.incrementDailyIssueCount(eq(clientId), anyString(), anyLong())).thenReturn(5L);

        TokenResponse response = tokenEndpoint.generateToken(clientId, clientSecret, clientIp);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotBlank();
    }

    @Test
    void generateToken_withDailyIssueLimitExceeded() {
        String clientId = "client-123";
        String clientSecret = "secret-abc";
        String clientIp = "127.0.0.1";

        when(clientRegistry.verifyClientSecret(clientId, clientSecret)).thenReturn(true);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.ENABLED))).thenReturn(true);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.CLIENT_ID))).thenReturn(clientId);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.CLIENT_SECRET))).thenReturn(clientSecret);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.CLIENT_NAME))).thenReturn("Test Client");
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.SCOPES))).thenReturn(List.of("read"));
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.TOKEN_VALID_DURATION))).thenReturn(2);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.REFRESH_TOKEN_VALID_DURATION))).thenReturn(720);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.RATE_LIMIT))).thenReturn(100);
        when(properties.isEnableDailyIssueLimit()).thenReturn(true);
        when(properties.isRevokePreviousTokensOnIssue()).thenReturn(false);
        when(properties.getTokenSecret()).thenReturn("test-secret-key-32chars-long!!!!");
        when(tokenStore.incrementDailyIssueCount(eq(clientId), anyString(), anyLong())).thenReturn(30L);

        assertThatThrownBy(() -> tokenEndpoint.generateToken(clientId, clientSecret, clientIp))
                .isInstanceOf(com.richie.contract.exception.BusinessException.class)
                .hasMessageContaining("当日签发令牌次数已达上限");
    }

    // ==================== generateToken Revoke Previous Tokens Tests ====================

    @Test
    void generateToken_withRevokePreviousTokens() {
        String clientId = "client-123";
        String clientSecret = "secret-abc";
        String clientIp = "127.0.0.1";
        String oldRefreshToken = "old-refresh-token";

        when(clientRegistry.verifyClientSecret(clientId, clientSecret)).thenReturn(true);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.ENABLED))).thenReturn(true);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.CLIENT_ID))).thenReturn(clientId);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.CLIENT_SECRET))).thenReturn(clientSecret);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.CLIENT_NAME))).thenReturn("Test Client");
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.SCOPES))).thenReturn(List.of("read"));
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.TOKEN_VALID_DURATION))).thenReturn(2);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.REFRESH_TOKEN_VALID_DURATION))).thenReturn(720);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.RATE_LIMIT))).thenReturn(100);
        when(properties.isEnableDailyIssueLimit()).thenReturn(false);
        when(properties.isRevokePreviousTokensOnIssue()).thenReturn(true);
        when(properties.getTokenSecret()).thenReturn("test-secret-key-32chars-long!!!!");
        when(tokenStore.getClientRefreshTokenIndex(clientId)).thenReturn(oldRefreshToken);

        TokenResponse response = tokenEndpoint.generateToken(clientId, clientSecret, clientIp);

        assertThat(response).isNotNull();
        verify(tokenStore).removeRefreshToken(oldRefreshToken);
        verify(tokenStore).removeClientRefreshTokenIndex(clientId);
    }

    @Test
    void generateToken_withRevokePreviousTokens_noPreviousToken() {
        String clientId = "client-123";
        String clientSecret = "secret-abc";
        String clientIp = "127.0.0.1";

        when(clientRegistry.verifyClientSecret(clientId, clientSecret)).thenReturn(true);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.ENABLED))).thenReturn(true);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.CLIENT_ID))).thenReturn(clientId);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.CLIENT_SECRET))).thenReturn(clientSecret);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.CLIENT_NAME))).thenReturn("Test Client");
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.SCOPES))).thenReturn(List.of("read"));
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.TOKEN_VALID_DURATION))).thenReturn(2);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.REFRESH_TOKEN_VALID_DURATION))).thenReturn(720);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.RATE_LIMIT))).thenReturn(100);
        when(properties.isEnableDailyIssueLimit()).thenReturn(false);
        when(properties.isRevokePreviousTokensOnIssue()).thenReturn(true);
        when(properties.getTokenSecret()).thenReturn("test-secret-key-32chars-long!!!!");
        when(tokenStore.getClientRefreshTokenIndex(clientId)).thenReturn(null);

        TokenResponse response = tokenEndpoint.generateToken(clientId, clientSecret, clientIp);

        assertThat(response).isNotNull();
        verify(tokenStore, never()).removeRefreshToken(anyString());
        verify(tokenStore, never()).removeClientRefreshTokenIndex(anyString());
    }

    @Test
    void generateToken_withNullScopes() {
        String clientId = "client-123";
        String clientSecret = "secret-abc";
        String clientIp = "127.0.0.1";

        when(clientRegistry.verifyClientSecret(clientId, clientSecret)).thenReturn(true);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.ENABLED))).thenReturn(true);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.CLIENT_ID))).thenReturn(clientId);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.CLIENT_SECRET))).thenReturn(clientSecret);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.CLIENT_NAME))).thenReturn("Test Client");
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.SCOPES))).thenReturn(null);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.TOKEN_VALID_DURATION))).thenReturn(2);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.REFRESH_TOKEN_VALID_DURATION))).thenReturn(720);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.RATE_LIMIT))).thenReturn(100);
        when(properties.isEnableDailyIssueLimit()).thenReturn(false);
        when(properties.isRevokePreviousTokensOnIssue()).thenReturn(false);
        when(properties.getTokenSecret()).thenReturn("test-secret-key-32chars-long!!!!");

        TokenResponse response = tokenEndpoint.generateToken(clientId, clientSecret, clientIp);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotBlank();
    }

    // ==================== revokeToken Tests ====================

    @Test
    void revokeToken_autoDetectRefreshToken() {
        String token = "no-dots-string";

        tokenEndpoint.revokeToken(token, null);

        verify(tokenStore).removeRefreshToken(token);
        verify(tokenStore, never()).addToBlacklist(anyString(), anyLong());
    }

    @Test
    void revokeToken_accessTokenWithoutSecret() {
        String accessToken = "valid.jwt.token";

        when(properties.getTokenSecret()).thenReturn(null);

        tokenEndpoint.revokeToken(accessToken, "access_token");

        verify(tokenStore, never()).addToBlacklist(anyString(), anyLong());
    }

    @Test
    void revokeToken_expiredAccessToken() {
        String accessToken = "expired.jwt.token";
        String tokenSecret = "test-secret-key-32chars-long!!!!";

        try (MockedStatic<JwtUtils> jwtUtils = mockStatic(JwtUtils.class)) {
            jwtUtils.when(() -> JwtUtils.verify(accessToken, tokenSecret)).thenReturn(true);
            jwtUtils.when(() -> JwtUtils.getExpiredTime(accessToken))
                    .thenReturn(new java.util.Date(System.currentTimeMillis() - 3600000));
            when(properties.getTokenSecret()).thenReturn(tokenSecret);

            tokenEndpoint.revokeToken(accessToken, "access_token");

            verify(tokenStore, never()).addToBlacklist(anyString(), anyLong());
        }
    }

    @Test
    void revokeToken_nullExpiredTime() {
        String accessToken = "valid.jwt.token";
        String tokenSecret = "test-secret-key-32chars-long!!!!";

        try (MockedStatic<JwtUtils> jwtUtils = mockStatic(JwtUtils.class)) {
            jwtUtils.when(() -> JwtUtils.verify(accessToken, tokenSecret)).thenReturn(true);
            jwtUtils.when(() -> JwtUtils.getExpiredTime(accessToken)).thenReturn(null);
            when(properties.getTokenSecret()).thenReturn(tokenSecret);

            tokenEndpoint.revokeToken(accessToken, "access_token");

            verify(tokenStore, never()).addToBlacklist(anyString(), anyLong());
        }
    }

    // ==================== generateToken Custom Duration Test ====================

    @Test
    void generateToken_withCustomTokenDuration() {
        String clientId = "client-123";
        String clientSecret = "secret-abc";
        String clientIp = "127.0.0.1";

        when(clientRegistry.verifyClientSecret(clientId, clientSecret)).thenReturn(true);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.ENABLED))).thenReturn(true);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.CLIENT_ID))).thenReturn(clientId);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.CLIENT_SECRET))).thenReturn(clientSecret);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.CLIENT_NAME))).thenReturn("Test Client");
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.SCOPES))).thenReturn(List.of("read"));
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.TOKEN_VALID_DURATION))).thenReturn(4);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.REFRESH_TOKEN_VALID_DURATION))).thenReturn(720);
        when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.RATE_LIMIT))).thenReturn(100);
        when(properties.isEnableDailyIssueLimit()).thenReturn(false);
        when(properties.isRevokePreviousTokensOnIssue()).thenReturn(false);
        when(properties.getTokenSecret()).thenReturn("test-secret-key-32chars-long!!!!");

        TokenResponse response = tokenEndpoint.generateToken(clientId, clientSecret, clientIp);

        assertThat(response).isNotNull();
        assertThat(response.getExpiresIn()).isEqualTo(14400L);
    }

    // ==================== verifyAccessToken Tests ====================

    @Test
    void verifyAccessToken_disabledClient_returnsNull() {
        String accessToken = "valid.jwt.token";
        String clientId = "client-123";
        String tokenSecret = "test-secret-key-32chars-long!!!!";

        try (MockedStatic<JwtUtils> jwtUtils = mockStatic(JwtUtils.class)) {
            jwtUtils.when(() -> JwtUtils.verify(accessToken, tokenSecret)).thenReturn(true);
            jwtUtils.when(() -> JwtUtils.getExpiredTime(accessToken))
                    .thenReturn(new java.util.Date(System.currentTimeMillis() + 3600000));
            jwtUtils.when(() -> JwtUtils.getArgument(accessToken, "clientId")).thenReturn(clientId);

            when(properties.getTokenSecret()).thenReturn(tokenSecret);
            when(tokenStore.isBlacklisted(accessToken)).thenReturn(false);
            when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.ENABLED), eq(ClientConfig.Field.SCOPES)))
                    .thenReturn(Map.of(
                            ClientConfig.Field.ENABLED, false,
                            ClientConfig.Field.SCOPES, List.of("read")
                    ));

            ClientConfig config = tokenEndpoint.verifyAccessToken(accessToken);

            assertThat(config).isNull();
        }
    }

    @Test
    void verifyAccessToken_nullFieldMap_returnsNull() {
        String accessToken = "valid.jwt.token";
        String clientId = "client-123";
        String tokenSecret = "test-secret-key-32chars-long!!!!";

        try (MockedStatic<JwtUtils> jwtUtils = mockStatic(JwtUtils.class)) {
            jwtUtils.when(() -> JwtUtils.verify(accessToken, tokenSecret)).thenReturn(true);
            jwtUtils.when(() -> JwtUtils.getExpiredTime(accessToken))
                    .thenReturn(new java.util.Date(System.currentTimeMillis() + 3600000));
            jwtUtils.when(() -> JwtUtils.getArgument(accessToken, "clientId")).thenReturn(clientId);

            when(properties.getTokenSecret()).thenReturn(tokenSecret);
            when(tokenStore.isBlacklisted(accessToken)).thenReturn(false);
            when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.ENABLED), eq(ClientConfig.Field.SCOPES)))
                    .thenReturn(null);

            ClientConfig config = tokenEndpoint.verifyAccessToken(accessToken);

            assertThat(config).isNull();
        }
    }

    @Test
    void verifyAccessToken_noScopes_returnsConfig() {
        String accessToken = "valid.jwt.token";
        String clientId = "client-123";
        String tokenSecret = "test-secret-key-32chars-long!!!!";

        try (MockedStatic<JwtUtils> jwtUtils = mockStatic(JwtUtils.class)) {
            jwtUtils.when(() -> JwtUtils.verify(accessToken, tokenSecret)).thenReturn(true);
            jwtUtils.when(() -> JwtUtils.getExpiredTime(accessToken))
                    .thenReturn(new java.util.Date(System.currentTimeMillis() + 3600000));
            jwtUtils.when(() -> JwtUtils.getArgument(accessToken, "clientId")).thenReturn(clientId);

            when(properties.getTokenSecret()).thenReturn(tokenSecret);
            when(tokenStore.isBlacklisted(accessToken)).thenReturn(false);
            when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.ENABLED), eq(ClientConfig.Field.SCOPES)))
                    .thenReturn(Map.of(
                            ClientConfig.Field.ENABLED, true
                    ));

            ClientConfig config = tokenEndpoint.verifyAccessToken(accessToken);

            assertThat(config).isNotNull();
            assertThat(config.getClientId()).isEqualTo(clientId);
            assertThat(config.getEnabled()).isTrue();
            assertThat(config.getScopes()).isEmpty();
        }
    }

    // ==================== getIpWhitelist Tests ====================

    @Test
    void getIpWhitelist_disabledClient_returnsNull() {
        String accessToken = "valid.jwt.token";
        String clientId = "client-123";
        String tokenSecret = "test-secret-key-32chars-long!!!!";

        try (MockedStatic<JwtUtils> jwtUtils = mockStatic(JwtUtils.class)) {
            jwtUtils.when(() -> JwtUtils.verify(accessToken, tokenSecret)).thenReturn(true);
            jwtUtils.when(() -> JwtUtils.getExpiredTime(accessToken))
                    .thenReturn(new java.util.Date(System.currentTimeMillis() + 3600000));
            jwtUtils.when(() -> JwtUtils.getArgument(accessToken, "clientId")).thenReturn(clientId);

            when(properties.getTokenSecret()).thenReturn(tokenSecret);
            when(tokenStore.isBlacklisted(accessToken)).thenReturn(false);
            when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.ENABLED), eq(ClientConfig.Field.IP_WHITELIST)))
                    .thenReturn(Map.of(
                            ClientConfig.Field.ENABLED, false,
                            ClientConfig.Field.IP_WHITELIST, List.of("127.0.0.1")
                    ));

            List<String> whitelist = tokenEndpoint.getIpWhitelist(accessToken);

            assertThat(whitelist).isNull();
        }
    }

    @Test
    void getIpWhitelist_nullFieldMap_returnsNull() {
        String accessToken = "valid.jwt.token";
        String clientId = "client-123";
        String tokenSecret = "test-secret-key-32chars-long!!!!";

        try (MockedStatic<JwtUtils> jwtUtils = mockStatic(JwtUtils.class)) {
            jwtUtils.when(() -> JwtUtils.verify(accessToken, tokenSecret)).thenReturn(true);
            jwtUtils.when(() -> JwtUtils.getExpiredTime(accessToken))
                    .thenReturn(new java.util.Date(System.currentTimeMillis() + 3600000));
            jwtUtils.when(() -> JwtUtils.getArgument(accessToken, "clientId")).thenReturn(clientId);

            when(properties.getTokenSecret()).thenReturn(tokenSecret);
            when(tokenStore.isBlacklisted(accessToken)).thenReturn(false);
            when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.ENABLED), eq(ClientConfig.Field.IP_WHITELIST)))
                    .thenReturn(null);

            List<String> whitelist = tokenEndpoint.getIpWhitelist(accessToken);

            assertThat(whitelist).isNull();
        }
    }

    // ==================== introspectToken Tests ====================

    @Test
    void introspectToken_withoutScopes_returnsActiveWithNoScope() {
        String accessToken = "valid.jwt.token";
        String clientId = "client-123";
        String tokenSecret = "test-secret-key-32chars-long!!!!";

        try (MockedStatic<JwtUtils> jwtUtils = mockStatic(JwtUtils.class)) {
            jwtUtils.when(() -> JwtUtils.verify(accessToken, tokenSecret)).thenReturn(true);
            jwtUtils.when(() -> JwtUtils.getExpiredTime(accessToken))
                    .thenReturn(new java.util.Date(System.currentTimeMillis() + 3600000));
            jwtUtils.when(() -> JwtUtils.getArgument(accessToken, "clientId")).thenReturn(clientId);

            when(properties.getTokenSecret()).thenReturn(tokenSecret);
            when(tokenStore.isBlacklisted(accessToken)).thenReturn(false);
            Map<ClientConfig.Field, Object> fieldMap = new HashMap<>();
            fieldMap.put(ClientConfig.Field.ENABLED, true);
            fieldMap.put(ClientConfig.Field.SCOPES, null);
            when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.ENABLED), eq(ClientConfig.Field.SCOPES)))
                    .thenReturn(fieldMap);

            TokenIntrospection introspection = tokenEndpoint.introspectToken(accessToken);

            assertThat(introspection).isNotNull();
            assertThat(introspection.isActive()).isTrue();
            assertThat(introspection.getClientId()).isEqualTo(clientId);
            assertThat(introspection.getScope()).isNull();
        }
    }

    // ==================== verifyAccessToken with audience Tests ====================

    @Test
    void verifyAccessToken_withAudienceMatch_returnsConfig() {
        String accessToken = "valid.jwt.token";
        String clientId = "client-123";
        String expectedAudience = "my-resource";
        String tokenSecret = "test-secret-key-32chars-long!!!!";

        try (MockedStatic<JwtUtils> jwtUtils = mockStatic(JwtUtils.class);
             MockedStatic<JWT> jwtStatic = mockStatic(JWT.class)) {
            jwtUtils.when(() -> JwtUtils.verify(accessToken, tokenSecret)).thenReturn(true);
            jwtUtils.when(() -> JwtUtils.getExpiredTime(accessToken))
                    .thenReturn(new java.util.Date(System.currentTimeMillis() + 3600000));
            jwtUtils.when(() -> JwtUtils.getArgument(accessToken, "clientId")).thenReturn(clientId);

            DecodedJWT decodedJwt = mock(DecodedJWT.class);
            Claim audClaim = mock(Claim.class);
            when(audClaim.isNull()).thenReturn(false);
            when(audClaim.asList(String.class)).thenReturn(List.of(expectedAudience));
            when(decodedJwt.getClaim("aud")).thenReturn(audClaim);
            jwtStatic.when(() -> JWT.decode(accessToken)).thenReturn(decodedJwt);

            when(properties.getTokenSecret()).thenReturn(tokenSecret);
            when(tokenStore.isBlacklisted(accessToken)).thenReturn(false);
            when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.ENABLED), eq(ClientConfig.Field.SCOPES)))
                    .thenReturn(Map.of(
                            ClientConfig.Field.ENABLED, true,
                            ClientConfig.Field.SCOPES, List.of("read")
                    ));

            ClientConfig config = tokenEndpoint.verifyAccessToken(accessToken, expectedAudience);

            assertThat(config).isNotNull();
            assertThat(config.getClientId()).isEqualTo(clientId);
        }
    }

    @Test
    void verifyAccessToken_withAudienceMismatch_returnsNull() {
        String accessToken = "valid.jwt.token";
        String clientId = "client-123";
        String tokenSecret = "test-secret-key-32chars-long!!!!";

        try (MockedStatic<JwtUtils> jwtUtils = mockStatic(JwtUtils.class);
             MockedStatic<JWT> jwtStatic = mockStatic(JWT.class)) {
            jwtUtils.when(() -> JwtUtils.verify(accessToken, tokenSecret)).thenReturn(true);
            jwtUtils.when(() -> JwtUtils.getExpiredTime(accessToken))
                    .thenReturn(new java.util.Date(System.currentTimeMillis() + 3600000));
            jwtUtils.when(() -> JwtUtils.getArgument(accessToken, "clientId")).thenReturn(clientId);

            DecodedJWT decodedJwt = mock(DecodedJWT.class);
            Claim audClaim = mock(Claim.class);
            when(audClaim.isNull()).thenReturn(false);
            when(audClaim.asList(String.class)).thenReturn(List.of("actual-resource"));
            when(decodedJwt.getClaim("aud")).thenReturn(audClaim);
            jwtStatic.when(() -> JWT.decode(accessToken)).thenReturn(decodedJwt);

            when(properties.getTokenSecret()).thenReturn(tokenSecret);
            when(tokenStore.isBlacklisted(accessToken)).thenReturn(false);

            ClientConfig config = tokenEndpoint.verifyAccessToken(accessToken, "expected-resource");

            assertThat(config).isNull();
        }
    }

    @Test
    void verifyAccessToken_withAudienceNull_skipsCheck() {
        String accessToken = "valid.jwt.token";
        String clientId = "client-123";
        String tokenSecret = "test-secret-key-32chars-long!!!!";

        try (MockedStatic<JwtUtils> jwtUtils = mockStatic(JwtUtils.class)) {
            jwtUtils.when(() -> JwtUtils.verify(accessToken, tokenSecret)).thenReturn(true);
            jwtUtils.when(() -> JwtUtils.getExpiredTime(accessToken))
                    .thenReturn(new java.util.Date(System.currentTimeMillis() + 3600000));
            jwtUtils.when(() -> JwtUtils.getArgument(accessToken, "clientId")).thenReturn(clientId);

            when(properties.getTokenSecret()).thenReturn(tokenSecret);
            when(tokenStore.isBlacklisted(accessToken)).thenReturn(false);
            when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.ENABLED), eq(ClientConfig.Field.SCOPES)))
                    .thenReturn(Map.of(
                            ClientConfig.Field.ENABLED, true,
                            ClientConfig.Field.SCOPES, List.of("read")
                    ));

            ClientConfig config = tokenEndpoint.verifyAccessToken(accessToken, null);

            assertThat(config).isNotNull();
            assertThat(config.getClientId()).isEqualTo(clientId);
        }
    }

    @Test
    void verifyAccessToken_withAudienceBlank_skipsCheck() {
        String accessToken = "valid.jwt.token";
        String clientId = "client-123";
        String tokenSecret = "test-secret-key-32chars-long!!!!";

        try (MockedStatic<JwtUtils> jwtUtils = mockStatic(JwtUtils.class)) {
            jwtUtils.when(() -> JwtUtils.verify(accessToken, tokenSecret)).thenReturn(true);
            jwtUtils.when(() -> JwtUtils.getExpiredTime(accessToken))
                    .thenReturn(new java.util.Date(System.currentTimeMillis() + 3600000));
            jwtUtils.when(() -> JwtUtils.getArgument(accessToken, "clientId")).thenReturn(clientId);

            when(properties.getTokenSecret()).thenReturn(tokenSecret);
            when(tokenStore.isBlacklisted(accessToken)).thenReturn(false);
            when(clientRegistry.getClientConfig(eq(clientId), eq(ClientConfig.Field.ENABLED), eq(ClientConfig.Field.SCOPES)))
                    .thenReturn(Map.of(
                            ClientConfig.Field.ENABLED, true,
                            ClientConfig.Field.SCOPES, List.of("read")
                    ));

            ClientConfig config = tokenEndpoint.verifyAccessToken(accessToken, "");

            assertThat(config).isNotNull();
            assertThat(config.getClientId()).isEqualTo(clientId);
        }
    }
}
