package com.richie.gateway.controller;

import com.richie.component.oauth.core.TokenEndpoint;
import com.richie.component.oauth.core.exception.InvalidClientException;
import com.richie.component.oauth.core.exception.InvalidGrantException;
import com.richie.component.oauth.core.exception.TokenExpiredException;
import com.richie.component.oauth.core.model.OAuth2ErrorResponse;
import com.richie.component.oauth.core.model.TokenIntrospection;
import com.richie.component.oauth.core.model.TokenResponse;
import com.richie.contract.gateway.model.OAuth2Constants;
import com.richie.gateway.utils.NetworkUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * OAuth2.0 Token 接口 Controller
 * <p>
 * 提供 OAuth2.0 Client Credentials 模式的 token 获取和刷新功能
 * <p>
 * 控制器仅负责接收请求并调用服务层，所有业务逻辑、参数解析、异常处理和响应构建均在服务层完成
 *
 * @author richie696
 * @version 1.0
 * @since 2025-12-16
 */
@Slf4j
@RestController
@RequestMapping(OAuth2Constants.OAUTH2_BASE)
@RequiredArgsConstructor
@ConditionalOnBean(TokenEndpoint.class)
public class OAuth2TokenController {

    private final TokenEndpoint tokenEndpoint;

    /**
     * OAuth2.0 Token 接口【OAuth2.0标准(RFC 6749)】
     * <p>
     * 支持两种 grant_type：
     * <ul>
     * <li>1. client_credentials: 获取 access_token 和 refresh_token<br>
     * - 必须通过 HTTP Basic Auth 提供 client_id 和 client_secret<br>
     * - scope 由系统根据客户端注册的权限范围自动授权，调用方不能自定义</li>
     * <li>2. refresh_token: 刷新 access_token<br>
     * - 不需要 HTTP Basic Auth，因为 refresh_token 本身已包含客户端信息<br>
     * - 只需在请求体中提供 refresh_token 参数</li>
     * </ul>
     *
     * @param exchange 请求交换器对象
     * @return Token 响应
     */
    @PostMapping(
            value = "/token",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Object>> token(ServerWebExchange exchange) {
        String ip = NetworkUtils.getIP(exchange.getRequest());
        return exchange.getFormData().flatMap(formData -> {
            String grantType = formData.getFirst("grant_type");
            if (OAuth2Constants.GRANT_TYPE_REFRESH_TOKEN.equals(grantType)) {
                String refreshToken = formData.getFirst("refresh_token");
                TokenResponse resp = tokenEndpoint.refreshToken(refreshToken, ip);
                return Mono.just(ResponseEntity.ok((Object) resp));
            }
            // client_credentials: prefer Basic Auth header
            String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
            String clientId = null;
            String clientSecret = null;
            if (authHeader != null && authHeader.startsWith("Basic ")) {
                String decoded = new String(Base64.getDecoder().decode(authHeader.substring(6)), StandardCharsets.UTF_8);
                String[] parts = decoded.split(":", 2);
                if (parts.length == 2) {
                    clientId = parts[0];
                    clientSecret = parts[1];
                }
            }
            if (clientId == null) {
                clientId = formData.getFirst("client_id");
                clientSecret = formData.getFirst("client_secret");
            }
            TokenResponse resp = tokenEndpoint.generateToken(clientId, clientSecret, ip);
            return Mono.just(ResponseEntity.ok((Object) resp));
        }).onErrorResume(InvalidClientException.class, e -> {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body((Object) new OAuth2ErrorResponse(e.getCode(), e.getMessage(), null)));
        }).onErrorResume(InvalidGrantException.class, e -> {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body((Object) new OAuth2ErrorResponse(e.getCode(), e.getMessage(), null)));
        }).onErrorResume(TokenExpiredException.class, e -> {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body((Object) new OAuth2ErrorResponse(e.getCode(), e.getMessage(), null)));
        });
    }

    /**
     * 令牌有效性校验接口【类似 OAuth2.0 Token Introspection】
     * <p>
     * 参数通过请求体（form-urlencoded）传递：token 和 token_type_hint（可选）
     *
     * @param exchange 请求交换器对象
     * @return 响应：active=true/false 及可选的附加信息
     */
    @Profile("!prod")
    @PostMapping(
            value = "/introspect",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<?>> introspect(ServerWebExchange exchange) {
        return exchange.getFormData().flatMap(formData -> {
            String token = formData.getFirst("token");
            TokenIntrospection result = tokenEndpoint.introspectToken(token);
            return Mono.just(ResponseEntity.ok(result));
        });
    }

    /**
     * 撤销 Token 接口（可选）【OAuth2.0标准(RFC 6749)】
     * <p>
     * 参数通过请求体（form-urlencoded）传递：token 和 token_type_hint（可选）
     *
     * @param exchange 请求交换器对象
     * @return 响应
     */
    @Profile("!prod")
    @PostMapping(
            value = "/revoke",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<?>> revoke(ServerWebExchange exchange) {
        return exchange.getFormData().flatMap(formData -> {
            String token = formData.getFirst("token");
            String tokenTypeHint = formData.getFirst("token_type_hint");
            tokenEndpoint.revokeToken(token, tokenTypeHint);
            return Mono.just(ResponseEntity.ok().build());
        });
    }
}
