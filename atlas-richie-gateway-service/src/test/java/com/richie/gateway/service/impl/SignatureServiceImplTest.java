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
package com.richie.gateway.service.impl;

import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.GlobalCacheManager;
import com.richie.component.cache.redis.manage.CacheLock;
import com.richie.context.utils.spring.JwtUtils;
import com.richie.contract.gateway.config.TokenFilterConfig;
import com.richie.contract.model.ApiResult;
import com.richie.contract.model.LoginUserPrincipal;
import com.richie.gateway.config.GatewayConfig;
import com.richie.gateway.utils.MfaTokenUtils;
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
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Disabled("JwtUtils.getArgument() is static and cannot be mocked due to ByteBuddy failure on servlet classes")
class SignatureServiceImplTest {

    private static final String SECRET = "test-secret-123456789012345678901234";
    private static final String TOKEN = "test.jwt.token";

    @Mock
    private GatewayConfig gatewayConfig;

    @Mock
    private MfaTokenUtils mfaTokenUtils;

    @Mock
    private GlobalCacheManager cacheManager;

    @Mock
    private com.richie.component.cache.ops.ValueOps valueOps;

    @Mock
    private com.richie.component.cache.ops.KeyOps keyOps;

    private SignatureServiceImpl service;

    private MockedStatic<GlobalCache> globalCacheMockedStatic;
    private MockedStatic<JwtUtils> jwtUtilsMockedStatic;

    @BeforeEach
    void setUp() throws Exception {
        injectCacheManager();

        globalCacheMockedStatic = mockStatic(GlobalCache.class);
        globalCacheMockedStatic.when(GlobalCache::value).thenReturn(valueOps);
        globalCacheMockedStatic.when(GlobalCache::key).thenReturn(keyOps);

        jwtUtilsMockedStatic = mockStatic(JwtUtils.class);

        TokenFilterConfig tokenCfg = new TokenFilterConfig();
        tokenCfg.setSecret(SECRET);
        tokenCfg.setBlacklistPath("blacklist:");
        tokenCfg.setTokenValidDuration(1);
        when(gatewayConfig.getToken()).thenReturn(tokenCfg);

        service = new SignatureServiceImpl(gatewayConfig, mfaTokenUtils);
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

    @Nested
    @DisplayName("createSignature 生成签名")
    class CreateSignatureTests {

        @Test
        @DisplayName("正常生成签名返回 token")
        void createSignature_normal_returnsToken() {
            jwtUtilsMockedStatic.when(() -> JwtUtils.generateJwtToken(any(LoginUserPrincipal.class), anyString(), anyLong()))
                    .thenReturn("signed-token");
            jwtUtilsMockedStatic.when(() -> JwtUtils.verify(anyString(), anyString()))
                    .thenReturn(true);
            jwtUtilsMockedStatic.when(() -> JwtUtils.getExpiredTime(anyString()))
                    .thenReturn(new Date(System.currentTimeMillis() + 3600000));

            LoginUserPrincipal principal = new LoginUserPrincipal();
            principal.setSignParams(new java.util.HashMap<>());
            principal.setUsername("testuser");
            ApiResult<LoginUserPrincipal> result = ApiResult.success(principal);

            String token = service.createSignature(result);

            assertThat(token).isEqualTo("signed-token");
            assertThat(principal.getSignParams()).isEmpty();
        }
    }

    @Nested
    @DisplayName("invalidToken 使令牌失效")
    class InvalidTokenTests {

        @Test
        @DisplayName("无效 token 返回成功")
        void invalidToken_invalidToken_returnsSuccess() {
            jwtUtilsMockedStatic.when(() -> JwtUtils.verify(TOKEN, SECRET)).thenReturn(false);

            ApiResult<Void> result = service.invalidToken(TOKEN);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("有效 token 验证通过后加入黑名单")
        void invalidToken_validToken_addsToBlacklist() {
            Date future = new Date(System.currentTimeMillis() + 3600000);
            jwtUtilsMockedStatic.when(() -> JwtUtils.verify(TOKEN, SECRET)).thenReturn(true);
            jwtUtilsMockedStatic.when(() -> JwtUtils.getExpiredTime(TOKEN)).thenReturn(future);
            jwtUtilsMockedStatic.when(() -> JwtUtils.getUsername(TOKEN)).thenReturn("testuser");

            ApiResult<Void> result = service.invalidToken(TOKEN);

            assertThat(result.isSuccess()).isTrue();
            verify(valueOps).set(eq("blacklist:" + TOKEN), eq("testuser"), anyLong());
        }
    }

    @Nested
    @DisplayName("logout 登出")
    class LogoutTests {

        @Test
        @DisplayName("空白 token 不处理")
        void logout_blankTokens_noOp() {
            ApiResult<Void> result = service.logout("", null);

            assertThat(result.isSuccess()).isTrue();
            verifyNoInteractions(valueOps);
        }

        @Test
        @DisplayName("有效 accessToken 加入黑名单并解析用户信息")
        void logout_validAccessToken_addsToBlacklist() {
            Date future = new Date(System.currentTimeMillis() + 3600000);
            jwtUtilsMockedStatic.when(() -> JwtUtils.verify(TOKEN, SECRET)).thenReturn(true);
            jwtUtilsMockedStatic.when(() -> JwtUtils.getExpiredTime(TOKEN)).thenReturn(future);
            jwtUtilsMockedStatic.when(() -> JwtUtils.getArgument(TOKEN, "userId")).thenReturn("u-001");
            jwtUtilsMockedStatic.when(() -> JwtUtils.getUsername(TOKEN)).thenReturn("testuser");
            jwtUtilsMockedStatic.when(() -> JwtUtils.getArgument(TOKEN, "tenantId")).thenReturn("tenant-1");

            ApiResult<Void> result = service.logout(TOKEN, null);

            assertThat(result.isSuccess()).isTrue();
            verify(valueOps).set(contains("blacklist:"), eq("1"), anyLong());
        }

        @Test
        @DisplayName("有效 MFA token 加入黑名单并补充用户信息")
        void logout_validMfaToken_addsToBlacklist() {
            String mfaToken = "mfa.token.here";
            Date future = new Date(System.currentTimeMillis() + 3600000);

            jwtUtilsMockedStatic.when(() -> JwtUtils.verify(TOKEN, SECRET)).thenReturn(false);
            when(mfaTokenUtils.isValidMfaToken(mfaToken)).thenReturn(true);
            when(mfaTokenUtils.getUserIdFromMfaToken(mfaToken)).thenReturn("u-002");
            when(mfaTokenUtils.getUsernameFromMfaToken(mfaToken)).thenReturn("mfauser");
            when(mfaTokenUtils.getTenantIdFromMfaToken(mfaToken)).thenReturn("tenant-2");
            jwtUtilsMockedStatic.when(() -> JwtUtils.getExpiredTime(mfaToken)).thenReturn(future);

            ApiResult<Void> result = service.logout(TOKEN, mfaToken);

            assertThat(result.isSuccess()).isTrue();
            verify(valueOps).set(contains("blacklist:"), eq("1"), anyLong());
            verify(keyOps).removeCache(contains("login:user:"));
        }

        @Test
        @DisplayName("无 userId 不移除用户缓存")
        void logout_noUserId_noUserCacheRemoval() {
            String mfaToken = "mfa.token.here";
            Date future = new Date(System.currentTimeMillis() + 3600000);

            jwtUtilsMockedStatic.when(() -> JwtUtils.verify(TOKEN, SECRET)).thenReturn(true);
            jwtUtilsMockedStatic.when(() -> JwtUtils.getExpiredTime(TOKEN)).thenReturn(future);
            jwtUtilsMockedStatic.when(() -> JwtUtils.getArgument(TOKEN, "userId")).thenReturn(null);
            jwtUtilsMockedStatic.when(() -> JwtUtils.getUsername(TOKEN)).thenReturn(null);
            jwtUtilsMockedStatic.when(() -> JwtUtils.getArgument(TOKEN, "tenantId")).thenReturn(null);
            when(mfaTokenUtils.isValidMfaToken(mfaToken)).thenReturn(false);

            ApiResult<Void> result = service.logout(TOKEN, mfaToken);

            assertThat(result.isSuccess()).isTrue();
            verify(keyOps, never()).removeCache(anyString());
        }
    }

    @Nested
    @DisplayName("notifyTenantExpired 通知租户过期")
    class NotifyTenantExpiredTests {

        @Test
        @DisplayName("始终返回成功")
        void notifyTenantExpired_alwaysSuccess() {
            ApiResult<Void> result = service.notifyTenantExpired("tenant-xyz");
            assertThat(result.isSuccess()).isTrue();
        }
    }
}
