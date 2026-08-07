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
package cn.richie696.component.oauth.authz;

import cn.richie696.component.oauth.authz.spi.AuthorizationCodeStore;
import cn.richie696.component.oauth.core.ClientRegistry;
import cn.richie696.component.oauth.core.config.OAuth2Properties;
import cn.richie696.contract.exception.BusinessException;
import cn.richie696.component.oauth.contract.OAuth2Constants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * OAuth 授权码流程的 Servlet 协议包装。
 * <p>
 * 提供 {@code GET /authorize}(校验请求并把上下文写入 Session 后跳登录页)和
 * {@code POST /authorize}(用户确认后生成授权码并 302 到 redirect_uri)两个入口;使用
 * {@link AuthorizationResponseBuilder} 构造回调 URL,保证 code/state/error 经 URL 编码不注入。
 * </p>
 * <p>
 * 处于 oauth-authz 模块的 HTTP 适配层位置:向下依赖 {@link ClientRegistry}、{@link AuthorizationCodeStore}
 * 与 {@link PKCESupport};和框架无关的 {@link AuthorizationService} 并行存在 —— 老系统可直接挂这个类,
 * 新业务或非 Servlet 场景(Reactive/Gateway)则改用 {@link AuthorizationService}。
 * </p>
 * <p>
 * 解决的问题:为已绑定 Servlet 容器的 OAuth Service 提供零改造的 /authorize 端点;同时让 Session
 * 化的请求上下文隔离在这个适配层内部,核心授权码生成与 PKCE 校验不感知 Servlet API。
 * </p>
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
    private final AuthorizationResponseBuilder responseBuilder = new AuthorizationResponseBuilder();

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
        String nonce = request.getParameter("nonce");

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
        request.getSession().setAttribute("oauth_nonce", nonce);

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
        String nonce = (String) request.getSession().getAttribute("oauth_nonce");

        if (StringUtils.isBlank(clientId)) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_REQUEST, "授权会话已过期，请重新发起授权请求");
        }

        String userId = request.getParameter("user_id");
        if (StringUtils.isBlank(userId)) {
            userId = "anonymous";
        }

        String code = generateAuthorizationCode(clientId, redirectUri, codeChallenge, codeChallengeMethod,
                scopes, userId, resource, nonce);

        String redirectUrl = responseBuilder.success(redirectUri, code, state).toString();

        request.getSession().removeAttribute("oauth_client_id");
        request.getSession().removeAttribute("oauth_redirect_uri");
        request.getSession().removeAttribute("oauth_code_challenge");
        request.getSession().removeAttribute("oauth_code_challenge_method");
        request.getSession().removeAttribute("oauth_state");
        request.getSession().removeAttribute("oauth_scope");
        request.getSession().removeAttribute("oauth_resource");
        request.getSession().removeAttribute("oauth_nonce");

        log.info("用户授权成功: userId={}, clientId={}", userId, clientId);
        response.sendRedirect(redirectUrl);
    }

    private String generateAuthorizationCode(
            String clientId,
            String redirectUri,
            String codeChallenge,
            String codeChallengeMethod,
            String scopes,
            String userId,
            String resource,
            String nonce
    ) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        java.util.List<String> scopeList = StringUtils.isNotBlank(scopes)
                ? java.util.Arrays.asList(scopes.split("\\s+"))
                : java.util.Collections.emptyList();

        if (StringUtils.isBlank(resource) && StringUtils.isBlank(nonce)) {
            // 保持旧版 AuthorizationCodeStore 和已有 Servlet 测试的调用契约。
            authzCodeStore.storeAuthorizationCode(code, clientId, redirectUri,
                    codeChallenge, codeChallengeMethod, scopeList, userId, 600);
        } else if (StringUtils.isBlank(resource)) {
            authzCodeStore.storeAuthorizationCode(code, clientId, redirectUri,
                    codeChallenge, codeChallengeMethod, scopeList, userId, nonce, 600);
        } else {
            authzCodeStore.storeAuthorizationCode(code, clientId, redirectUri,
                    codeChallenge, codeChallengeMethod, scopeList, userId, resource, nonce, 600);
        }

        log.debug("生成授权码: code={}, clientId={}", code, clientId);
        return code;
    }
}
