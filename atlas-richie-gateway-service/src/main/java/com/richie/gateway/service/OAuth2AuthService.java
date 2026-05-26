package com.richie.gateway.service;

import com.richie.gateway.vo.OAuth2TokenResponseVO;
import com.richie.gateway.vo.ThirdPartyClientConfigVO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.List;

/**
 * OAuth2.0 认证服务接口
 * <p>
 * 负责 OAuth2.0 认证核心业务逻辑：
 * - Token 生成（Client Credentials 模式）
 * - Token 刷新（Refresh Token 模式）
 * - Token 验证
 * - Token 撤销
 *
 * @author richie696
 * @version 1.0
 * @since 2025-12-16
 */
public interface OAuth2AuthService {

    /**
     * 生成 access_token 和 refresh_token（Client Credentials 模式）
     * <p>
     * scope 由系统根据客户端注册的权限范围自动授权，调用方不能自定义
     *
     * @param clientId     客户端ID（从 HTTP Basic Auth 获取）
     * @param clientSecret 客户端密钥（从 HTTP Basic Auth 获取）
     * @param ip           当前请求 IP（用于令牌归属绑定）
     * @return Token 响应
     */
    OAuth2TokenResponseVO generateToken(String clientId, String clientSecret, String ip);

    /**
     * 刷新 access_token（Refresh Token 模式）
     *
     * @param refreshToken 刷新令牌
     * @param ip           当前请求 IP（用于令牌归属绑定校验）
     * @return Token 响应
     */
    OAuth2TokenResponseVO refreshToken(String refreshToken, String ip);

    /**
     * 验证 access_token
     *
     * @param accessToken 访问令牌
     * @return 客户端配置，如果 token 无效则返回 null
     */
    ThirdPartyClientConfigVO verifyAccessToken(String accessToken);

    /**
     * 根据 access_token 获取客户端 IP 白名单（接口鉴权轻量场景）
     *
     * @param accessToken 访问令牌
     * @return IP 白名单，token 无效或客户端不可用时返回 null
     */
    List<String> getIpWhitelist(String accessToken);

    /**
     * 撤销 token（可选）
     *
     * @param token        令牌（access_token 或 refresh_token）
     * @param tokenTypeHint 令牌类型提示（可选）
     */
    void revokeToken(String token, String tokenTypeHint);

    /**
     * 处理 Token 请求（包含参数解析、校验、业务处理和响应构建）
     * <p>
     * 此方法封装了完整的 token 请求处理流程：
     * - 解析 form-urlencoded 请求体
     * - 解析 HTTP Basic Auth（仅 client_credentials 模式需要）
     * - 参数校验
     * - 调用相应的业务方法
     * - 异常处理和响应构建
     *
     * @param exchange ServerWebExchange（用于获取请求信息和解析请求体）
     * @return HTTP 响应（包含状态码和响应体）
     */
    Mono<ResponseEntity<?>> requestToken(ServerWebExchange exchange);

    /**
     * 处理 Token 验证请求（Token Introspection）
     * <p>
     * 此方法封装了完整的 token 验证请求处理流程：
     * - 解析 form-urlencoded 请求体
     * - 参数校验
     * - 调用验证方法
     * - 异常处理和响应构建
     *
     * @param exchange ServerWebExchange（用于解析请求体）
     * @return HTTP 响应（包含状态码和响应体）
     */
    Mono<ResponseEntity<?>> introspectToken(ServerWebExchange exchange);

    /**
     * 处理 Token 撤销请求
     * <p>
     * 此方法封装了完整的 token 撤销请求处理流程：
     * - 解析 form-urlencoded 请求体
     * - 参数校验
     * - 调用撤销方法
     * - 异常处理和响应构建
     *
     * @param exchange ServerWebExchange（用于解析请求体）
     * @return HTTP 响应（包含状态码和响应体）
     */
    Mono<ResponseEntity<?>> revokeTokenRequest(ServerWebExchange exchange);
}
