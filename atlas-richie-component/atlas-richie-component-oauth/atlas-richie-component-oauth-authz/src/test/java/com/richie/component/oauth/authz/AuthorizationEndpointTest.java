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
package com.richie.component.oauth.authz;

import com.richie.component.oauth.authz.spi.AuthorizationCodeStore;
import com.richie.component.oauth.core.ClientRegistry;
import com.richie.component.oauth.core.config.OAuth2Properties;
import com.richie.contract.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthorizationEndpoint 测试")
class AuthorizationEndpointTest {

    @Mock
    private ClientRegistry clientRegistry;
    @Mock
    private AuthorizationCodeStore authzCodeStore;
    @Mock
    private PKCESupport pkceSupport;
    @Mock
    private OAuth2Properties properties;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private HttpSession session;

    private AuthorizationEndpoint authorizationEndpoint;

    @BeforeEach
    void setUp() {
        authorizationEndpoint = new AuthorizationEndpoint(
                clientRegistry, authzCodeStore, pkceSupport, properties);
    }

    @Test
    @DisplayName("handleAuthorizationRequest 当参数有效时重定向")
    void handleAuthorizationRequest_redirectsWhenValidParams() throws IOException {
        String clientId = "client-123";
        String redirectUri = "https://example.com/callback";
        String codeChallenge = "challenge-xyz";
        String codeChallengeMethod = "S256";
        String state = "random-state";
        String scope = "read write";
        String resource = "https://api.example.com";

        when(request.getParameter("client_id")).thenReturn(clientId);
        when(request.getParameter("redirect_uri")).thenReturn(redirectUri);
        when(request.getParameter("code_challenge")).thenReturn(codeChallenge);
        when(request.getParameter("code_challenge_method")).thenReturn(codeChallengeMethod);
        when(request.getParameter("state")).thenReturn(state);
        when(request.getParameter("scope")).thenReturn(scope);
        when(request.getParameter("resource")).thenReturn(resource);
        when(request.getSession()).thenReturn(session);
        when(clientRegistry.isClientValid(clientId)).thenReturn(true);

        authorizationEndpoint.handleAuthorizationRequest(request, response);

        verify(response).sendRedirect("/login/oauth");
        verify(session).setAttribute("oauth_client_id", clientId);
        verify(session).setAttribute("oauth_redirect_uri", redirectUri);
        verify(session).setAttribute("oauth_code_challenge", codeChallenge);
        verify(session).setAttribute("oauth_code_challenge_method", codeChallengeMethod);
        verify(session).setAttribute("oauth_state", state);
        verify(session).setAttribute("oauth_scope", scope);
        verify(session).setAttribute("oauth_resource", resource);
    }

    @Test
    @DisplayName("handleAuthorizationRequest 当缺少 code_challenge 时抛出异常")
    void handleAuthorizationRequest_throwsOnMissingCodeChallenge() {
        String clientId = "client-123";
        String redirectUri = "https://example.com/callback";

        when(request.getParameter("client_id")).thenReturn(clientId);
        when(request.getParameter("redirect_uri")).thenReturn(redirectUri);
        when(request.getParameter("code_challenge")).thenReturn(null);
        when(request.getParameter("code_challenge_method")).thenReturn("S256");

        assertThatThrownBy(() -> authorizationEndpoint.handleAuthorizationRequest(request, response))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("code_challenge 参数必填");
    }

    @Test
    @DisplayName("handleAuthorizationRequest 当 code_challenge_method 非 S256 时抛出异常")
    void handleAuthorizationRequest_throwsOnNonS256Method() {
        String clientId = "client-123";
        String redirectUri = "https://example.com/callback";
        String codeChallenge = "challenge-xyz";

        when(request.getParameter("client_id")).thenReturn(clientId);
        when(request.getParameter("redirect_uri")).thenReturn(redirectUri);
        when(request.getParameter("code_challenge")).thenReturn(codeChallenge);
        when(request.getParameter("code_challenge_method")).thenReturn("plain");

        assertThatThrownBy(() -> authorizationEndpoint.handleAuthorizationRequest(request, response))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅支持 S256 method");
    }

    @Test
    @DisplayName("handleAuthorizationConsent 生成代码并重定向")
    void handleAuthorizationConsent_generatesCodeAndRedirects() throws IOException {
        String clientId = "client-123";
        String redirectUri = "https://example.com/callback";
        String codeChallenge = "challenge-xyz";
        String codeChallengeMethod = "S256";
        String state = "random-state";
        String scopes = "read write";
        String resource = "https://api.example.com";

        when(request.getSession()).thenReturn(session);
        when(session.getAttribute("oauth_client_id")).thenReturn(clientId);
        when(session.getAttribute("oauth_redirect_uri")).thenReturn(redirectUri);
        when(session.getAttribute("oauth_code_challenge")).thenReturn(codeChallenge);
        when(session.getAttribute("oauth_code_challenge_method")).thenReturn(codeChallengeMethod);
        when(session.getAttribute("oauth_state")).thenReturn(state);
        when(session.getAttribute("oauth_scope")).thenReturn(scopes);
        when(session.getAttribute("oauth_resource")).thenReturn(resource);
        when(request.getParameter("user_id")).thenReturn("user-456");
        when(clientRegistry.isClientValid(clientId)).thenReturn(true);

        authorizationEndpoint.handleAuthorizationConsent(request, response);

        verify(authzCodeStore).storeAuthorizationCode(
                anyString(),
                eq(clientId),
                eq(redirectUri),
                eq(codeChallenge),
                eq(codeChallengeMethod),
                argThat(list -> list.containsAll(Arrays.asList("read", "write"))),
                eq("user-456"),
                eq(600L)
        );
        verify(response).sendRedirect(startsWith(redirectUri + "?code="));
        verify(session).removeAttribute("oauth_client_id");
        verify(session).removeAttribute("oauth_redirect_uri");
    }

    @Test
    @DisplayName("handleAuthorizationConsent 当 clientId 为空时抛出异常")
    void handleAuthorizationConsent_throwsWhenClientIdBlank() {
        when(request.getSession()).thenReturn(session);
        when(session.getAttribute("oauth_client_id")).thenReturn(null);

        assertThatThrownBy(() -> authorizationEndpoint.handleAuthorizationConsent(request, response))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("授权会话已过期");
    }

    @Test
    @DisplayName("handleAuthorizationRequest 当客户端无效时抛出异常")
    void handleAuthorizationRequest_throwsWhenClientInvalid() {
        String clientId = "invalid-client";
        String redirectUri = "https://example.com/callback";
        String codeChallenge = "challenge-xyz";
        String codeChallengeMethod = "S256";

        when(request.getParameter("client_id")).thenReturn(clientId);
        when(request.getParameter("redirect_uri")).thenReturn(redirectUri);
        when(request.getParameter("code_challenge")).thenReturn(codeChallenge);
        when(request.getParameter("code_challenge_method")).thenReturn(codeChallengeMethod);
        when(clientRegistry.isClientValid(clientId)).thenReturn(false);

        assertThatThrownBy(() -> authorizationEndpoint.handleAuthorizationRequest(request, response))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户端不存在或已禁用");
    }

    @Test
    @DisplayName("handleAuthorizationConsent 使用 anonymous 当 user_id 为空")
    void handleAuthorizationConsent_usesAnonymousWhenUserIdBlank() throws IOException {
        String clientId = "client-123";
        String redirectUri = "https://example.com/callback";
        String codeChallenge = "challenge-xyz";
        String codeChallengeMethod = "S256";

        when(request.getSession()).thenReturn(session);
        when(session.getAttribute("oauth_client_id")).thenReturn(clientId);
        when(session.getAttribute("oauth_redirect_uri")).thenReturn(redirectUri);
        when(session.getAttribute("oauth_code_challenge")).thenReturn(codeChallenge);
        when(session.getAttribute("oauth_code_challenge_method")).thenReturn(codeChallengeMethod);
        when(session.getAttribute("oauth_state")).thenReturn(null);
        when(session.getAttribute("oauth_scope")).thenReturn(null);
        when(session.getAttribute("oauth_resource")).thenReturn(null);
        when(request.getParameter("user_id")).thenReturn(null);
        when(clientRegistry.isClientValid(clientId)).thenReturn(true);

        authorizationEndpoint.handleAuthorizationConsent(request, response);

        verify(authzCodeStore).storeAuthorizationCode(
                anyString(),
                eq(clientId),
                anyString(),
                anyString(),
                anyString(),
                any(),
                eq("anonymous"),
                eq(600L)
        );
    }
}
