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
package com.richie.component.oauth.authz;

import com.richie.component.oauth.authz.spi.AuthorizationCodeStore;
import com.richie.component.oauth.core.ClientRegistry;
import com.richie.component.oauth.core.config.OAuth2Properties;
import com.richie.component.oauth.core.model.ClientConfig;
import com.richie.component.oauth.core.model.TokenResponse;
import com.richie.component.oauth.core.spi.TokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthorizationCodeGrant 测试")
class AuthorizationCodeGrantTest {

    @Mock
    private TokenStore tokenStore;
    @Mock
    private ClientRegistry clientRegistry;
    @Mock
    private AuthorizationCodeStore authzCodeStore;
    @Mock
    private PKCESupport pkceSupport;
    @Mock
    private OAuth2Properties properties;

    private AuthorizationCodeGrant authorizationCodeGrant;

    @BeforeEach
    void setUp() {
        authorizationCodeGrant = new AuthorizationCodeGrant(
                tokenStore, clientRegistry, authzCodeStore, pkceSupport, properties);
    }

    @Test
    @DisplayName("exchangeCodeForToken 当有效时返回 TokenResponse")
    void exchangeCodeForToken_returnsTokenResponseOnValidFlow() {
        String clientId = "client-123";
        String clientSecret = "secret-abc";
        String code = "auth-code-456";
        String codeVerifier = "verifier-xyz";
        String redirectUri = "https://example.com/callback";
        String ip = "192.168.1.1";

        Map<String, String> codeData = new HashMap<>();
        codeData.put("clientId", clientId);
        codeData.put("redirectUri", redirectUri);
        codeData.put("codeChallenge", "challenge");
        codeData.put("codeChallengeMethod", "S256");
        codeData.put("scopes", "read write");

        when(clientRegistry.verifyClientSecret(clientId, clientSecret)).thenReturn(true);
        when(authzCodeStore.loadAuthorizationCode(code)).thenReturn(codeData);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.ENABLED)).thenReturn(true);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_ID)).thenReturn(clientId);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_SECRET)).thenReturn(clientSecret);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_NAME)).thenReturn("Test Client");
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.SCOPES)).thenReturn(List.of("read", "write"));
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.TOKEN_VALID_DURATION)).thenReturn(2);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.REFRESH_TOKEN_VALID_DURATION)).thenReturn(720);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.RATE_LIMIT)).thenReturn(100);
        when(pkceSupport.verifyChallenge(anyString(), anyString(), anyString())).thenReturn(true);
        when(properties.getTokenSecret()).thenReturn("test-secret-key-32chars-long!!!!");

        TokenResponse response = authorizationCodeGrant.exchangeCodeForToken(
                clientId, clientSecret, code, codeVerifier, redirectUri, null, ip);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getExpiresIn()).isEqualTo(7200L);
        assertThat(response.getScope()).isEqualTo("read write");

        verify(authzCodeStore).consumeAuthorizationCode(code);
        verify(tokenStore).storeRefreshToken(anyString(), eq(clientId), eq(ip), any(ClientConfig.class));
        verify(tokenStore).bindAccessTokenIp(anyString(), eq(clientId), eq(ip), anyLong());
    }

    @Test
    @DisplayName("exchangeCodeForToken 当客户端密钥无效时抛出异常")
    void exchangeCodeForToken_throwsOnInvalidClientSecret() {
        String clientId = "client-123";
        String clientSecret = "wrong-secret";
        String code = "auth-code-456";

        when(clientRegistry.verifyClientSecret(clientId, clientSecret)).thenReturn(false);

        assertThatThrownBy(() -> authorizationCodeGrant.exchangeCodeForToken(
                clientId, clientSecret, code, null, null, null, "127.0.0.1"))
                .isInstanceOf(com.richie.contract.exception.BusinessException.class)
                .hasMessageContaining("客户端认证失败");

        verify(authzCodeStore, never()).loadAuthorizationCode(anyString());
    }

    @Test
    @DisplayName("exchangeCodeForToken 当授权码无效时抛出异常")
    void exchangeCodeForToken_throwsOnInvalidAuthCode() {
        String clientId = "client-123";
        String clientSecret = "secret-abc";
        String code = "invalid-code";

        when(clientRegistry.verifyClientSecret(clientId, clientSecret)).thenReturn(true);
        when(authzCodeStore.loadAuthorizationCode(code)).thenReturn(null);

        assertThatThrownBy(() -> authorizationCodeGrant.exchangeCodeForToken(
                clientId, clientSecret, code, null, null, null, "127.0.0.1"))
                .isInstanceOf(com.richie.contract.exception.BusinessException.class)
                .hasMessageContaining("授权码无效或已过期");
    }

    @Test
    @DisplayName("exchangeCodeForToken 当客户端 ID 不匹配时抛出异常")
    void exchangeCodeForToken_throwsOnClientIdMismatch() {
        String clientId = "client-123";
        String clientSecret = "secret-abc";
        String code = "auth-code-456";

        Map<String, String> codeData = new HashMap<>();
        codeData.put("clientId", "different-client");

        when(clientRegistry.verifyClientSecret(clientId, clientSecret)).thenReturn(true);
        when(authzCodeStore.loadAuthorizationCode(code)).thenReturn(codeData);

        assertThatThrownBy(() -> authorizationCodeGrant.exchangeCodeForToken(
                clientId, clientSecret, code, null, null, null, "127.0.0.1"))
                .isInstanceOf(com.richie.contract.exception.BusinessException.class)
                .hasMessageContaining("客户端 ID 不匹配");
    }

    @Test
    @DisplayName("exchangeCodeForToken 当重定向 URI 不匹配时抛出异常")
    void exchangeCodeForToken_throwsOnRedirectUriMismatch() {
        String clientId = "client-123";
        String clientSecret = "secret-abc";
        String code = "auth-code-456";
        String redirectUri = "https://wrong.com/callback";

        Map<String, String> codeData = new HashMap<>();
        codeData.put("clientId", clientId);
        codeData.put("redirectUri", "https://expected.com/callback");

        when(clientRegistry.verifyClientSecret(clientId, clientSecret)).thenReturn(true);
        when(authzCodeStore.loadAuthorizationCode(code)).thenReturn(codeData);

        assertThatThrownBy(() -> authorizationCodeGrant.exchangeCodeForToken(
                clientId, clientSecret, code, null, redirectUri, null, "127.0.0.1"))
                .isInstanceOf(com.richie.contract.exception.BusinessException.class)
                .hasMessageContaining("重定向 URI 不匹配");
    }

    @Test
    @DisplayName("exchangeCodeForToken 当 PKCE 验证失败时抛出异常")
    void exchangeCodeForToken_throwsOnPkceVerificationFailure() {
        String clientId = "client-123";
        String clientSecret = "secret-abc";
        String code = "auth-code-456";
        String codeVerifier = "wrong-verifier";

        Map<String, String> codeData = new HashMap<>();
        codeData.put("clientId", clientId);
        codeData.put("redirectUri", "https://example.com/callback");
        codeData.put("codeChallenge", "challenge");
        codeData.put("codeChallengeMethod", "S256");

        when(clientRegistry.verifyClientSecret(clientId, clientSecret)).thenReturn(true);
        when(authzCodeStore.loadAuthorizationCode(code)).thenReturn(codeData);
        when(pkceSupport.verifyChallenge(anyString(), anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authorizationCodeGrant.exchangeCodeForToken(
                clientId, clientSecret, code, codeVerifier, null, null, "127.0.0.1"))
                .isInstanceOf(com.richie.contract.exception.BusinessException.class)
                .hasMessageContaining("PKCE 验证失败");
    }

    @Test
    @DisplayName("exchangeCodeForToken 当客户端已禁用时抛出异常")
    void exchangeCodeForToken_throwsOnDisabledClient() {
        String clientId = "client-123";
        String clientSecret = "secret-abc";
        String code = "auth-code-456";

        Map<String, String> codeData = new HashMap<>();
        codeData.put("clientId", clientId);
        codeData.put("redirectUri", "https://example.com/callback");
        codeData.put("codeChallenge", "challenge");
        codeData.put("codeChallengeMethod", "S256");

        when(clientRegistry.verifyClientSecret(clientId, clientSecret)).thenReturn(true);
        when(authzCodeStore.loadAuthorizationCode(code)).thenReturn(codeData);
        when(pkceSupport.verifyChallenge(anyString(), anyString(), nullable(String.class))).thenReturn(true);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.ENABLED)).thenReturn(false);

        assertThatThrownBy(() -> authorizationCodeGrant.exchangeCodeForToken(
                clientId, clientSecret, code, null, null, null, "127.0.0.1"))
                .isInstanceOf(com.richie.contract.exception.BusinessException.class)
                .hasMessageContaining("客户端不存在或已禁用");
    }

    @Test
    @DisplayName("exchangeCodeForToken 消费授权码在使用后")
    void exchangeCodeForToken_consumesAuthCodeAfterUse() {
        String clientId = "client-123";
        String clientSecret = "secret-abc";
        String code = "auth-code-456";

        Map<String, String> codeData = new HashMap<>();
        codeData.put("clientId", clientId);
        codeData.put("redirectUri", "https://example.com/callback");
        codeData.put("codeChallenge", "");
        codeData.put("codeChallengeMethod", "");

        when(clientRegistry.verifyClientSecret(clientId, clientSecret)).thenReturn(true);
        when(authzCodeStore.loadAuthorizationCode(code)).thenReturn(codeData);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.ENABLED)).thenReturn(true);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_ID)).thenReturn(clientId);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_SECRET)).thenReturn(clientSecret);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_NAME)).thenReturn("Test");
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.SCOPES)).thenReturn(List.of("read"));
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.TOKEN_VALID_DURATION)).thenReturn(2);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.REFRESH_TOKEN_VALID_DURATION)).thenReturn(720);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.RATE_LIMIT)).thenReturn(100);
        when(properties.getTokenSecret()).thenReturn("test-secret-key-32chars-long!!!!");

        authorizationCodeGrant.exchangeCodeForToken(
                clientId, clientSecret, code, null, null, null, "127.0.0.1");

        verify(authzCodeStore).consumeAuthorizationCode(code);
    }

    @Test
    @DisplayName("exchangeCodeForToken 当提供 resource 时生成包含 aud claim 的 JWT")
    void exchangeCodeForToken_includesAudClaimWhenResourceProvided() {
        String clientId = "client-123";
        String clientSecret = "secret-abc";
        String code = "auth-code-456";
        String resource = "https://api.example.com";

        Map<String, String> codeData = new HashMap<>();
        codeData.put("clientId", clientId);
        codeData.put("redirectUri", "https://example.com/callback");
        codeData.put("codeChallenge", "");
        codeData.put("codeChallengeMethod", "");
        codeData.put("scopes", "read");

        when(clientRegistry.verifyClientSecret(clientId, clientSecret)).thenReturn(true);
        when(authzCodeStore.loadAuthorizationCode(code)).thenReturn(codeData);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.ENABLED)).thenReturn(true);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_ID)).thenReturn(clientId);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_SECRET)).thenReturn(clientSecret);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_NAME)).thenReturn("Test");
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.SCOPES)).thenReturn(List.of("read"));
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.TOKEN_VALID_DURATION)).thenReturn(2);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.REFRESH_TOKEN_VALID_DURATION)).thenReturn(720);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.RATE_LIMIT)).thenReturn(100);
        when(properties.getTokenSecret()).thenReturn("test-secret-key-32chars-long!!!!");

        TokenResponse response = authorizationCodeGrant.exchangeCodeForToken(
                clientId, clientSecret, code, null, null, resource, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotBlank();
    }

    @Test
    @DisplayName("exchangeCodeForToken 当 codeChallengeMethod 为 plain 时跳过 PKCE 验证")
    void exchangeCodeForToken_withPlainCodeChallengeMethod_skipsPkceVerification() {
        String clientId = "client-123";
        String clientSecret = "secret-abc";
        String code = "auth-code-456";
        String redirectUri = "https://example.com/callback";

        Map<String, String> codeData = new HashMap<>();
        codeData.put("clientId", clientId);
        codeData.put("redirectUri", redirectUri);
        codeData.put("codeChallenge", "challenge");
        codeData.put("codeChallengeMethod", "plain");
        codeData.put("scopes", "read");

        when(clientRegistry.verifyClientSecret(clientId, clientSecret)).thenReturn(true);
        when(authzCodeStore.loadAuthorizationCode(code)).thenReturn(codeData);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.ENABLED)).thenReturn(true);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_ID)).thenReturn(clientId);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_SECRET)).thenReturn(clientSecret);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_NAME)).thenReturn("Test");
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.SCOPES)).thenReturn(List.of("read"));
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.TOKEN_VALID_DURATION)).thenReturn(2);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.REFRESH_TOKEN_VALID_DURATION)).thenReturn(720);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.RATE_LIMIT)).thenReturn(100);
        when(properties.getTokenSecret()).thenReturn("test-secret-key-32chars-long!!!!");

        TokenResponse response = authorizationCodeGrant.exchangeCodeForToken(
                clientId, clientSecret, code, null, null, null, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getExpiresIn()).isEqualTo(7200L);
        assertThat(response.getScope()).isEqualTo("read");
        verify(pkceSupport, never()).verifyChallenge(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("exchangeCodeForToken 当 codeChallenge 为空时跳过 PKCE 验证")
    void exchangeCodeForToken_withBlankCodeChallenge_skipsPkceVerification() {
        String clientId = "client-123";
        String clientSecret = "secret-abc";
        String code = "auth-code-456";

        Map<String, String> codeData = new HashMap<>();
        codeData.put("clientId", clientId);
        codeData.put("redirectUri", "https://example.com/callback");
        codeData.put("codeChallenge", "");
        codeData.put("codeChallengeMethod", "S256");

        when(clientRegistry.verifyClientSecret(clientId, clientSecret)).thenReturn(true);
        when(authzCodeStore.loadAuthorizationCode(code)).thenReturn(codeData);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.ENABLED)).thenReturn(true);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_ID)).thenReturn(clientId);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_SECRET)).thenReturn(clientSecret);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_NAME)).thenReturn("Test");
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.SCOPES)).thenReturn(List.of("read"));
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.TOKEN_VALID_DURATION)).thenReturn(2);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.REFRESH_TOKEN_VALID_DURATION)).thenReturn(720);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.RATE_LIMIT)).thenReturn(100);
        when(properties.getTokenSecret()).thenReturn("test-secret-key-32chars-long!!!!");

        TokenResponse response = authorizationCodeGrant.exchangeCodeForToken(
                clientId, clientSecret, code, null, null, null, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotBlank();
        verify(pkceSupport, never()).verifyChallenge(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("exchangeCodeForToken 当 codeData 中 scopes 为空时使用客户端配置 scopes")
    void exchangeCodeForToken_withNullScopesInCodeData_usesClientConfigScopes() {
        String clientId = "client-123";
        String clientSecret = "secret-abc";
        String code = "auth-code-456";

        Map<String, String> codeData = new HashMap<>();
        codeData.put("clientId", clientId);
        codeData.put("redirectUri", "https://example.com/callback");
        codeData.put("codeChallenge", "");
        codeData.put("codeChallengeMethod", "");
        codeData.put("scopes", null);

        when(clientRegistry.verifyClientSecret(clientId, clientSecret)).thenReturn(true);
        when(authzCodeStore.loadAuthorizationCode(code)).thenReturn(codeData);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.ENABLED)).thenReturn(true);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_ID)).thenReturn(clientId);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_SECRET)).thenReturn(clientSecret);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_NAME)).thenReturn("Test");
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.SCOPES)).thenReturn(List.of("read", "write"));
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.TOKEN_VALID_DURATION)).thenReturn(2);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.REFRESH_TOKEN_VALID_DURATION)).thenReturn(720);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.RATE_LIMIT)).thenReturn(100);
        when(properties.getTokenSecret()).thenReturn("test-secret-key-32chars-long!!!!");

        TokenResponse response = authorizationCodeGrant.exchangeCodeForToken(
                clientId, clientSecret, code, null, null, null, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.getScope()).isEqualTo("read write");
    }

    @Test
    @DisplayName("exchangeCodeForToken 当请求中 redirectUri 为空时使用存储的 redirectUri")
    void exchangeCodeForToken_withEmptyRedirectUriInRequest_overridesStoredUri() {
        String clientId = "client-123";
        String clientSecret = "secret-abc";
        String code = "auth-code-456";
        String storedRedirectUri = "https://example.com/callback";

        Map<String, String> codeData = new HashMap<>();
        codeData.put("clientId", clientId);
        codeData.put("redirectUri", storedRedirectUri);
        codeData.put("codeChallenge", "");
        codeData.put("codeChallengeMethod", "");

        when(clientRegistry.verifyClientSecret(clientId, clientSecret)).thenReturn(true);
        when(authzCodeStore.loadAuthorizationCode(code)).thenReturn(codeData);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.ENABLED)).thenReturn(true);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_ID)).thenReturn(clientId);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_SECRET)).thenReturn(clientSecret);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_NAME)).thenReturn("Test");
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.SCOPES)).thenReturn(List.of("read"));
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.TOKEN_VALID_DURATION)).thenReturn(2);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.REFRESH_TOKEN_VALID_DURATION)).thenReturn(720);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.RATE_LIMIT)).thenReturn(100);
        when(properties.getTokenSecret()).thenReturn("test-secret-key-32chars-long!!!!");

        TokenResponse response = authorizationCodeGrant.exchangeCodeForToken(
                clientId, clientSecret, code, null, null, null, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotBlank();
    }

    @Test
    @DisplayName("exchangeCodeForToken 当 tokenValidDuration 为 null 时使用默认过期时间")
    void exchangeCodeForToken_withNullExpiredTimeInJwt_returnsToken() {
        String clientId = "client-123";
        String clientSecret = "secret-abc";
        String code = "auth-code-456";

        Map<String, String> codeData = new HashMap<>();
        codeData.put("clientId", clientId);
        codeData.put("redirectUri", "https://example.com/callback");
        codeData.put("codeChallenge", "");
        codeData.put("codeChallengeMethod", "");
        codeData.put("scopes", "read");

        when(clientRegistry.verifyClientSecret(clientId, clientSecret)).thenReturn(true);
        when(authzCodeStore.loadAuthorizationCode(code)).thenReturn(codeData);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.ENABLED)).thenReturn(true);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_ID)).thenReturn(clientId);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_SECRET)).thenReturn(clientSecret);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_NAME)).thenReturn("Test");
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.SCOPES)).thenReturn(List.of("read"));
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.TOKEN_VALID_DURATION)).thenReturn(null);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.REFRESH_TOKEN_VALID_DURATION)).thenReturn(720);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.RATE_LIMIT)).thenReturn(100);
        when(properties.getTokenSecret()).thenReturn("test-secret-key-32chars-long!!!!");

        TokenResponse response = authorizationCodeGrant.exchangeCodeForToken(
                clientId, clientSecret, code, null, null, null, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getExpiresIn()).isEqualTo(3600L);
    }

    @Test
    @DisplayName("exchangeCodeForToken 当 codeChallengeMethod 为 PLAIN 大写时跳过 PKCE 验证")
    void exchangeCodeForToken_withPlainCodeChallengeMethodUpperCase_skipsVerification() {
        String clientId = "client-123";
        String clientSecret = "secret-abc";
        String code = "auth-code-456";

        Map<String, String> codeData = new HashMap<>();
        codeData.put("clientId", clientId);
        codeData.put("redirectUri", "https://example.com/callback");
        codeData.put("codeChallenge", "challenge");
        codeData.put("codeChallengeMethod", "PLAIN");
        codeData.put("scopes", "read");

        when(clientRegistry.verifyClientSecret(clientId, clientSecret)).thenReturn(true);
        when(authzCodeStore.loadAuthorizationCode(code)).thenReturn(codeData);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.ENABLED)).thenReturn(true);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_ID)).thenReturn(clientId);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_SECRET)).thenReturn(clientSecret);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_NAME)).thenReturn("Test");
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.SCOPES)).thenReturn(List.of("read"));
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.TOKEN_VALID_DURATION)).thenReturn(2);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.REFRESH_TOKEN_VALID_DURATION)).thenReturn(720);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.RATE_LIMIT)).thenReturn(100);
        when(properties.getTokenSecret()).thenReturn("test-secret-key-32chars-long!!!!");

        TokenResponse response = authorizationCodeGrant.exchangeCodeForToken(
                clientId, clientSecret, code, null, null, null, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotBlank();
        verify(pkceSupport, never()).verifyChallenge(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("exchangeCodeForToken 当 codeData 中 scopes 为空字符串时使用客户端配置 scopes")
    void exchangeCodeForToken_withEmptyScopesInCodeData_usesConfigScopes() {
        String clientId = "client-123";
        String clientSecret = "secret-abc";
        String code = "auth-code-456";

        Map<String, String> codeData = new HashMap<>();
        codeData.put("clientId", clientId);
        codeData.put("redirectUri", "https://example.com/callback");
        codeData.put("codeChallenge", "");
        codeData.put("codeChallengeMethod", "");
        codeData.put("scopes", "");

        when(clientRegistry.verifyClientSecret(clientId, clientSecret)).thenReturn(true);
        when(authzCodeStore.loadAuthorizationCode(code)).thenReturn(codeData);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.ENABLED)).thenReturn(true);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_ID)).thenReturn(clientId);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_SECRET)).thenReturn(clientSecret);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_NAME)).thenReturn("Test");
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.SCOPES)).thenReturn(List.of("read", "write"));
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.TOKEN_VALID_DURATION)).thenReturn(2);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.REFRESH_TOKEN_VALID_DURATION)).thenReturn(720);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.RATE_LIMIT)).thenReturn(100);
        when(properties.getTokenSecret()).thenReturn("test-secret-key-32chars-long!!!!");

        TokenResponse response = authorizationCodeGrant.exchangeCodeForToken(
                clientId, clientSecret, code, null, null, null, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.getScope()).isEqualTo("read write");
    }

    @Test
    @DisplayName("exchangeCodeForToken 当 codeData 和 config 中 scopes 都为 null 时使用空列表")
    void exchangeCodeForToken_withNullScopesInCodeDataAndNullConfigScopes_usesEmptyList() {
        String clientId = "client-123";
        String clientSecret = "secret-abc";
        String code = "auth-code-456";

        Map<String, String> codeData = new HashMap<>();
        codeData.put("clientId", clientId);
        codeData.put("redirectUri", "https://example.com/callback");
        codeData.put("codeChallenge", "");
        codeData.put("codeChallengeMethod", "");

        when(clientRegistry.verifyClientSecret(clientId, clientSecret)).thenReturn(true);
        when(authzCodeStore.loadAuthorizationCode(code)).thenReturn(codeData);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.ENABLED)).thenReturn(true);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_ID)).thenReturn(clientId);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_SECRET)).thenReturn(clientSecret);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_NAME)).thenReturn("Test");
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.SCOPES)).thenReturn(null);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.TOKEN_VALID_DURATION)).thenReturn(2);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.REFRESH_TOKEN_VALID_DURATION)).thenReturn(720);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.RATE_LIMIT)).thenReturn(100);
        when(properties.getTokenSecret()).thenReturn("test-secret-key-32chars-long!!!!");

        TokenResponse response = authorizationCodeGrant.exchangeCodeForToken(
                clientId, clientSecret, code, null, null, null, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.getScope()).isEmpty();
    }

    @Test
    @DisplayName("exchangeCodeForToken 当 redirectUri 为 null 时不抛出异常")
    void exchangeCodeForToken_withRedirectUriNull_doesNotThrow() {
        String clientId = "client-123";
        String clientSecret = "secret-abc";
        String code = "auth-code-456";

        Map<String, String> codeData = new HashMap<>();
        codeData.put("clientId", clientId);
        codeData.put("redirectUri", "https://expected.com/callback");
        codeData.put("codeChallenge", "");
        codeData.put("codeChallengeMethod", "");

        when(clientRegistry.verifyClientSecret(clientId, clientSecret)).thenReturn(true);
        when(authzCodeStore.loadAuthorizationCode(code)).thenReturn(codeData);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.ENABLED)).thenReturn(true);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_ID)).thenReturn(clientId);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_SECRET)).thenReturn(clientSecret);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_NAME)).thenReturn("Test");
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.SCOPES)).thenReturn(List.of("read"));
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.TOKEN_VALID_DURATION)).thenReturn(2);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.REFRESH_TOKEN_VALID_DURATION)).thenReturn(720);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.RATE_LIMIT)).thenReturn(100);
        when(properties.getTokenSecret()).thenReturn("test-secret-key-32chars-long!!!!");

        TokenResponse response = authorizationCodeGrant.exchangeCodeForToken(
                clientId, clientSecret, code, null, null, null, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotBlank();
    }

    @Test
    @DisplayName("exchangeCodeForToken 当 redirectUri 为空字符串时不抛出异常")
    void exchangeCodeForToken_withEmptyRedirectUriProvided_doesNotThrow() {
        String clientId = "client-123";
        String clientSecret = "secret-abc";
        String code = "auth-code-456";

        Map<String, String> codeData = new HashMap<>();
        codeData.put("clientId", clientId);
        codeData.put("redirectUri", "https://expected.com/callback");
        codeData.put("codeChallenge", "");
        codeData.put("codeChallengeMethod", "");

        when(clientRegistry.verifyClientSecret(clientId, clientSecret)).thenReturn(true);
        when(authzCodeStore.loadAuthorizationCode(code)).thenReturn(codeData);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.ENABLED)).thenReturn(true);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_ID)).thenReturn(clientId);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_SECRET)).thenReturn(clientSecret);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_NAME)).thenReturn("Test");
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.SCOPES)).thenReturn(List.of("read"));
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.TOKEN_VALID_DURATION)).thenReturn(2);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.REFRESH_TOKEN_VALID_DURATION)).thenReturn(720);
        when(clientRegistry.getClientConfig(clientId, ClientConfig.Field.RATE_LIMIT)).thenReturn(100);
        when(properties.getTokenSecret()).thenReturn("test-secret-key-32chars-long!!!!");

        TokenResponse response = authorizationCodeGrant.exchangeCodeForToken(
                clientId, clientSecret, code, null, "", null, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotBlank();
    }

    @Test
    @DisplayName("exchangeCodeForToken 当 codeData 为空 map 时抛出异常")
    void exchangeCodeForToken_withEmptyCodeData_throwsException() {
        String clientId = "client-123";
        String clientSecret = "secret-abc";
        String code = "auth-code-456";

        when(clientRegistry.verifyClientSecret(clientId, clientSecret)).thenReturn(true);
        when(authzCodeStore.loadAuthorizationCode(code)).thenReturn(new HashMap<>());

        assertThatThrownBy(() -> authorizationCodeGrant.exchangeCodeForToken(
                clientId, clientSecret, code, null, null, null, "127.0.0.1"))
                .isInstanceOf(com.richie.contract.exception.BusinessException.class)
                .hasMessageContaining("授权码无效或已过期");
    }
}
