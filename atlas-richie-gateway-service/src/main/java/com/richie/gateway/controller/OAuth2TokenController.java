package com.richie.gateway.controller;

import com.richie.contract.gateway.model.OAuth2Constants;
import com.richie.gateway.service.OAuth2AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

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
@RestController
@RequestMapping(OAuth2Constants.OAUTH2_BASE)
@RequiredArgsConstructor
public class OAuth2TokenController {

    private final OAuth2AuthService authService;

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
    public Mono<ResponseEntity<?>> token(ServerWebExchange exchange) {
        return authService.requestToken(exchange);
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
        return authService.introspectToken(exchange);
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
        return authService.revokeTokenRequest(exchange);
    }

}
