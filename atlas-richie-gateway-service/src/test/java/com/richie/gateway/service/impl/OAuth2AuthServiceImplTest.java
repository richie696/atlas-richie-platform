package com.richie.gateway.service.impl;

import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.GlobalCacheManager;
import com.richie.component.cache.redis.manage.CacheLock;
import com.richie.context.utils.spring.JwtUtils;
import com.richie.contract.exception.BusinessException;
import com.richie.gateway.config.GatewayConfig;
import com.richie.gateway.config.IOAuthFilterConfig;
import com.richie.gateway.service.OAuth2ClientService;
import com.richie.gateway.vo.OAuth2TokenResponseVO;
import com.richie.gateway.vo.ThirdPartyClientConfigVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.security.SecureRandom;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Disabled("JwtUtils static method mocking fails on final class with ByteBuddy")
class OAuth2AuthServiceImplTest {

    private static final String CLIENT_ID = "client-001";
    private static final String CLIENT_SECRET = "secret-001";
    private static final String CLIENT_NAME = "Test Client";
    private static final String IP = "192.168.1.100";
    private static final String TOKEN_SECRET = "token-secret-12345678901234567890";
    private static final String ACCESS_TOKEN = "access.token.here";

    @Mock
    private OAuth2ClientService clientService;

    @Mock
    private GatewayConfig gatewayConfig;

    @Mock
    private GlobalCacheManager cacheManager;

    @Mock
    private com.richie.component.cache.ops.ValueOps valueOps;

    @Mock
    private com.richie.component.cache.ops.StructOps structOps;

    @Mock
    private com.richie.component.cache.ops.KeyOps keyOps;

    @Mock
    private com.richie.component.cache.ops.LockOps lockOps;

    private OAuth2AuthServiceImpl service;

    private MockedStatic<GlobalCache> globalCacheMockedStatic;
    private MockedStatic<JwtUtils> jwtUtilsMockedStatic;

    @BeforeEach
    void setUp() throws Exception {
        injectCacheManager();

        globalCacheMockedStatic = mockStatic(GlobalCache.class);
        globalCacheMockedStatic.when(GlobalCache::value).thenReturn(valueOps);
        globalCacheMockedStatic.when(GlobalCache::struct).thenReturn(structOps);
        globalCacheMockedStatic.when(GlobalCache::key).thenReturn(keyOps);
        globalCacheMockedStatic.when(GlobalCache::lock).thenReturn(lockOps);

        jwtUtilsMockedStatic = mockStatic(JwtUtils.class);

        IOAuthFilterConfig oauthConfig = mock(IOAuthFilterConfig.class);
        when(gatewayConfig.getInterfaceAuth()).thenReturn(oauthConfig);
        when(oauthConfig.getTokenSecret()).thenReturn(TOKEN_SECRET);

        service = new OAuth2AuthServiceImpl(clientService, gatewayConfig);
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
        if (jwtUtilsMockedStatic != null) jwtUtilsMockedStatic.close();
    }

    private ThirdPartyClientConfigVO createClientConfig(boolean enabled, List<String> scopes) {
        return ThirdPartyClientConfigVO.builder()
                .clientId(CLIENT_ID)
                .clientSecret(CLIENT_SECRET)
                .clientName(CLIENT_NAME)
                .enabled(enabled)
                .scopes(scopes)
                .tokenValidDuration(1)
                .refreshTokenValidDuration(24)
                .build();
    }

    @Nested
    @DisplayName("generateToken 生成访问令牌")
    class GenerateTokenTests {

        @Test
        @DisplayName("客户端密钥错误时抛出异常")
        void generateToken_wrongSecret_throws() {
            when(clientService.verifyClientSecret(CLIENT_ID, CLIENT_SECRET)).thenReturn(false);

            assertThatThrownBy(() -> service.generateToken(CLIENT_ID, CLIENT_SECRET, IP))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("客户端认证失败");
        }

        @Test
        @DisplayName("客户端不存在或禁用时抛出异常")
        void generateToken_clientDisabled_throws() {
            when(clientService.verifyClientSecret(CLIENT_ID, CLIENT_SECRET)).thenReturn(true);
            when(clientService.getClientConfig(eq(CLIENT_ID), any())).thenReturn(null);

            assertThatThrownBy(() -> service.generateToken(CLIENT_ID, CLIENT_SECRET, IP))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("客户端不存在或已被禁用");
        }

        @Test
        @DisplayName("客户端有效时返回令牌响应")
        void generateToken_validClient_returnsTokenResponse() {
            when(clientService.verifyClientSecret(CLIENT_ID, CLIENT_SECRET)).thenReturn(true);
            when(clientService.getClientConfig(eq(CLIENT_ID), any()))
                    .thenReturn("true", CLIENT_SECRET, CLIENT_NAME, "true",
                            List.of("read", "write"), 1, 24);

            jwtUtilsMockedStatic.when(() -> JwtUtils.verify(anyString(), anyString())).thenReturn(true);
            jwtUtilsMockedStatic.when(() -> JwtUtils.getArgument(anyString(), eq("clientId"))).thenReturn(CLIENT_ID);

            OAuth2TokenResponseVO response = service.generateToken(CLIENT_ID, CLIENT_SECRET, IP);

            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isNotBlank();
            assertThat(response.getTokenType()).isEqualTo("Bearer");
            assertThat(response.getExpiresIn()).isGreaterThan(0);
            assertThat(response.getRefreshToken()).isNotBlank();
        }

        @Test
        @DisplayName("客户端无 scopes 时正常返回")
        void generateToken_noScopes_returnsTokenResponse() {
            when(clientService.verifyClientSecret(CLIENT_ID, CLIENT_SECRET)).thenReturn(true);
            when(clientService.getClientConfig(eq(CLIENT_ID), any()))
                    .thenReturn("true", CLIENT_SECRET, CLIENT_NAME, "true",
                            null, 1, 24);

            OAuth2TokenResponseVO response = service.generateToken(CLIENT_ID, CLIENT_SECRET, IP);

            assertThat(response.getAccessToken()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("refreshToken 刷新访问令牌")
    class RefreshTokenTests {

        @Test
        @DisplayName("空白 refreshToken 时抛出异常")
        void refreshToken_blankToken_throws() {
            assertThatThrownBy(() -> service.refreshToken("", IP))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("刷新令牌不能为空");
        }

        @Test
        @DisplayName("无效 refreshToken 时抛出异常")
        void refreshToken_invalidToken_throws() {
            jwtUtilsMockedStatic.when(() -> JwtUtils.verify(anyString(), anyString())).thenReturn(false);

            assertThatThrownBy(() -> service.refreshToken("invalid-token", IP))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("verifyAccessToken 验证访问令牌")
    class VerifyAccessTokenTests {

        @Test
        @DisplayName("空白 token 返回 null")
        void verifyAccessToken_blank_returnsNull() {
            ThirdPartyClientConfigVO result = service.verifyAccessToken("");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("tokenSecret 未配置时返回 null")
        void verifyAccessToken_noSecret_returnsNull() {
            when(gatewayConfig.getInterfaceAuth()).thenReturn(null);

            ThirdPartyClientConfigVO result = service.verifyAccessToken(ACCESS_TOKEN);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("签名验证失败返回 null")
        void verifyAccessToken_verifyFails_returnsNull() {
            jwtUtilsMockedStatic.when(() -> JwtUtils.verify(ACCESS_TOKEN, TOKEN_SECRET)).thenReturn(false);

            ThirdPartyClientConfigVO result = service.verifyAccessToken(ACCESS_TOKEN);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("令牌在黑名单中返回 null")
        void verifyAccessToken_blacklisted_returnsNull() {
            jwtUtilsMockedStatic.when(() -> JwtUtils.verify(ACCESS_TOKEN, TOKEN_SECRET)).thenReturn(true);
            jwtUtilsMockedStatic.when(() -> JwtUtils.getExpiredTime(ACCESS_TOKEN))
                    .thenReturn(new Date(System.currentTimeMillis() + 3600000));
            jwtUtilsMockedStatic.when(() -> JwtUtils.getArgument(ACCESS_TOKEN, "clientId"))
                    .thenReturn(CLIENT_ID);
            when(keyOps.hasKey(anyString())).thenReturn(true);

            ThirdPartyClientConfigVO result = service.verifyAccessToken(ACCESS_TOKEN);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("令牌过期返回 null")
        void verifyAccessToken_expired_returnsNull() {
            jwtUtilsMockedStatic.when(() -> JwtUtils.verify(ACCESS_TOKEN, TOKEN_SECRET)).thenReturn(true);
            jwtUtilsMockedStatic.when(() -> JwtUtils.getExpiredTime(ACCESS_TOKEN))
                    .thenReturn(new Date(System.currentTimeMillis() - 1000));
            when(keyOps.hasKey(anyString())).thenReturn(false);

            ThirdPartyClientConfigVO result = service.verifyAccessToken(ACCESS_TOKEN);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("有效令牌返回客户端配置")
        void verifyAccessToken_valid_returnsConfig() {
            jwtUtilsMockedStatic.when(() -> JwtUtils.verify(ACCESS_TOKEN, TOKEN_SECRET)).thenReturn(true);
            jwtUtilsMockedStatic.when(() -> JwtUtils.getExpiredTime(ACCESS_TOKEN))
                    .thenReturn(new Date(System.currentTimeMillis() + 3600000));
            jwtUtilsMockedStatic.when(() -> JwtUtils.getArgument(ACCESS_TOKEN, "clientId")).thenReturn(CLIENT_ID);
            when(keyOps.hasKey(anyString())).thenReturn(false);
            when(clientService.getClientConfig(eq(CLIENT_ID), any(), any()))
                    .thenReturn(java.util.Map.of(
                            ThirdPartyClientConfigVO.Field.ENABLED, true,
                            ThirdPartyClientConfigVO.Field.SCOPES, List.of("read")
                    ));

            ThirdPartyClientConfigVO result = service.verifyAccessToken(ACCESS_TOKEN);

            assertThat(result).isNotNull();
            assertThat(result.getClientId()).isEqualTo(CLIENT_ID);
        }
    }

    @Nested
    @DisplayName("getIpWhitelist 获取 IP 白名单")
    class GetIpWhitelistTests {

        @Test
        @DisplayName("无效 token 返回 null")
        void getIpWhitelist_invalidToken_returnsNull() {
            when(gatewayConfig.getInterfaceAuth()).thenReturn(null);

            List<String> result = service.getIpWhitelist(ACCESS_TOKEN);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("有效 token 但客户端禁用返回 null")
        void getIpWhitelist_clientDisabled_returnsNull() {
            jwtUtilsMockedStatic.when(() -> JwtUtils.verify(ACCESS_TOKEN, TOKEN_SECRET)).thenReturn(true);
            jwtUtilsMockedStatic.when(() -> JwtUtils.getExpiredTime(ACCESS_TOKEN))
                    .thenReturn(new Date(System.currentTimeMillis() + 3600000));
            jwtUtilsMockedStatic.when(() -> JwtUtils.getArgument(ACCESS_TOKEN, "clientId")).thenReturn(CLIENT_ID);
            when(keyOps.hasKey(anyString())).thenReturn(false);
            when(clientService.getClientConfig(eq(CLIENT_ID), any(), any()))
                    .thenReturn(java.util.Map.of(
                            ThirdPartyClientConfigVO.Field.ENABLED, false,
                            ThirdPartyClientConfigVO.Field.IP_WHITELIST, List.of()
                    ));

            List<String> result = service.getIpWhitelist(ACCESS_TOKEN);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("revokeToken 撤销令牌")
    class RevokeTokenTests {

        @Test
        @DisplayName("空白 token 不处理")
        void revokeToken_blankToken_noOp() {
            service.revokeToken("", null);

            verifyNoInteractions(keyOps);
            verifyNoInteractions(valueOps);
        }

        @Test
        @DisplayName("refresh_token 类型删除缓存")
        void revokeToken_refreshToken_deletesCache() {
            service.revokeToken("refresh-token-abc", "refresh_token");

            verify(keyOps).removeCache(contains("refresh-token-abc"));
        }

        @Test
        @DisplayName("access_token 加入黑名单")
        void revokeToken_accessToken_addsToBlacklist() {
            jwtUtilsMockedStatic.when(() -> JwtUtils.verify(ACCESS_TOKEN, TOKEN_SECRET)).thenReturn(true);
            jwtUtilsMockedStatic.when(() -> JwtUtils.getExpiredTime(ACCESS_TOKEN))
                    .thenReturn(new Date(System.currentTimeMillis() + 3600000));

            service.revokeToken(ACCESS_TOKEN, "access_token");

            verify(valueOps).set(contains("blacklist"), eq("1"), anyLong());
            verify(keyOps).removeCache(contains("bind"));
        }

        @Test
        @DisplayName("无 tokenSecret 时跳过黑名单")
        void revokeToken_noSecret_skipsBlacklist() {
            when(gatewayConfig.getInterfaceAuth()).thenReturn(mock(IOAuthFilterConfig.class));
            when(gatewayConfig.getInterfaceAuth().getTokenSecret()).thenReturn(null);

            service.revokeToken(ACCESS_TOKEN, "access_token");

            verify(valueOps, never()).set(anyString(), any(), anyLong());
        }
    }
}
