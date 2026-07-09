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
package com.richie.component.oauth.authz;

import com.richie.component.oauth.authz.spi.AuthorizationCodeStore;
import com.richie.component.oauth.core.ClientRegistry;
import com.richie.component.oauth.core.config.OAuth2Properties;
import com.richie.contract.exception.BusinessException;
import com.richie.contract.gateway.model.OAuth2Constants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * OAuth 2.1 授权端点
 * <p>
 * 处理授权请求（GET /authorize）和用户授权确认（POST /authorize）。
 *
 * @author richie696
 * @since 2026-06-12
 */
@Slf4j
public class AuthorizationEndpoint {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ClientRegistry clientRegistry;
    private final AuthorizationCodeStore authzCodeStore;
    private final PKCESupport pkceSupport;
    private final OAuth2Properties properties;

    public AuthorizationEndpoint(
            ClientRegistry clientRegistry,
            AuthorizationCodeStore authzCodeStore,
            PKCESupport pkceSupport,
            OAuth2Properties properties
    ) {
        this.clientRegistry = clientRegistry;
        this.authzCodeStore = authzCodeStore;
        this.pkceSupport = pkceSupport;
        this.properties = properties;
    }

    /**
     * 处理 GET /authorize 授权请求
     * <p>
     * 验证请求参数（client_id, redirect_uri, code_challenge, code_challenge_method），
     * 然后重定向到登录页面。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    public void handleAuthorizationRequest(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String clientId = request.getParameter("client_id");
        String redirectUri = request.getParameter("redirect_uri");
        String codeChallenge = request.getParameter("code_challenge");
        String codeChallengeMethod = request.getParameter("code_challenge_method");
        String state = request.getParameter("state");
        String scopes = request.getParameter("scope");
        String resource = request.getParameter("resource");

        if (StringUtils.isBlank(clientId)) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_REQUEST, "client_id 参数必填");
        }
        if (StringUtils.isBlank(redirectUri)) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_REQUEST, "redirect_uri 参数必填");
        }
        if (StringUtils.isBlank(codeChallenge)) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_REQUEST, "code_challenge 参数必填");
        }
        if (!"S256".equalsIgnoreCase(codeChallengeMethod)) {
            throw new BusinessException("invalid_code_challenge_method", "仅支持 S256 method");
        }

        if (!clientRegistry.isClientValid(clientId)) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_CLIENT, "客户端不存在或已禁用");
        }

        log.debug("授权请求: clientId={}, redirectUri={}, state={}", clientId, redirectUri, state);

        request.getSession().setAttribute("oauth_client_id", clientId);
        request.getSession().setAttribute("oauth_redirect_uri", redirectUri);
        request.getSession().setAttribute("oauth_code_challenge", codeChallenge);
        request.getSession().setAttribute("oauth_code_challenge_method", codeChallengeMethod);
        request.getSession().setAttribute("oauth_state", state);
        request.getSession().setAttribute("oauth_scope", scopes);
        request.getSession().setAttribute("oauth_resource", resource);

        response.sendRedirect("/login/oauth");
    }

    /**
     * 处理 POST /authorize 用户授权确认
     * <p>
     * 用户登录并确认授权后，生成授权码并重定向到客户端 redirect_uri。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    public void handleAuthorizationConsent(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String clientId = (String) request.getSession().getAttribute("oauth_client_id");
        String redirectUri = (String) request.getSession().getAttribute("oauth_redirect_uri");
        String codeChallenge = (String) request.getSession().getAttribute("oauth_code_challenge");
        String codeChallengeMethod = (String) request.getSession().getAttribute("oauth_code_challenge_method");
        String state = (String) request.getSession().getAttribute("oauth_state");
        String scopes = (String) request.getSession().getAttribute("oauth_scope");
        String resource = (String) request.getSession().getAttribute("oauth_resource");

        if (StringUtils.isBlank(clientId)) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_REQUEST, "授权会话已过期，请重新发起授权请求");
        }

        String userId = request.getParameter("user_id");
        if (StringUtils.isBlank(userId)) {
            userId = "anonymous";
        }

        String code = generateAuthorizationCode(clientId, redirectUri, codeChallenge, codeChallengeMethod,
                scopes, userId);

        StringBuilder redirectUrl = new StringBuilder(redirectUri);
        redirectUrl.append(code.contains("?") ? "&" : "?");
        redirectUrl.append("code=").append(code);
        if (StringUtils.isNotBlank(state)) {
            redirectUrl.append("&state=").append(state);
        }

        request.getSession().removeAttribute("oauth_client_id");
        request.getSession().removeAttribute("oauth_redirect_uri");
        request.getSession().removeAttribute("oauth_code_challenge");
        request.getSession().removeAttribute("oauth_code_challenge_method");
        request.getSession().removeAttribute("oauth_state");
        request.getSession().removeAttribute("oauth_scope");
        request.getSession().removeAttribute("oauth_resource");

        log.info("用户授权成功: userId={}, clientId={}", userId, clientId);
        response.sendRedirect(redirectUrl.toString());
    }

    private String generateAuthorizationCode(
            String clientId,
            String redirectUri,
            String codeChallenge,
            String codeChallengeMethod,
            String scopes,
            String userId
    ) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        java.util.List<String> scopeList = StringUtils.isNotBlank(scopes)
                ? java.util.Arrays.asList(scopes.split("\\s+"))
                : java.util.Collections.emptyList();

        authzCodeStore.storeAuthorizationCode(
                code,
                clientId,
                redirectUri,
                codeChallenge,
                codeChallengeMethod,
                scopeList,
                userId,
                600
        );

        log.debug("生成授权码: code={}, clientId={}", code, clientId);
        return code;
    }
}
