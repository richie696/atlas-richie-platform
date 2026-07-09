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
package com.richie.component.grpc.interceptor;

import com.richie.context.utils.spring.JwtUtils;
import com.richie.contract.constant.GlobalConstants;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;

/**
 * gRPC 服务端 JWT 鉴权拦截器
 *
 * <p>从请求 Metadata 中提取 JWT、验证签名，拒绝无效令牌。
 * 验证通过后将用户名写入 Metadata 供下游拦截器使用。</p>
 *
 * <p>JWT secret 通过构造参数注入，建议从 {@code platform.grpc.server.auth-secret} 配置读取。</p>
 *
 * <p>推荐注册顺序紧随 Header 拦截器之后（Header 先提取上下文，Auth 再校验）。</p>
 *
 * @author richie696
 * @version 1.0
 * @since 1.0.0
 */
@Slf4j
public final class GrpcServerAuthInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> ACCESS_TOKEN_KEY =
            Metadata.Key.of(GlobalConstants.X_ACCESS_TOKEN, Metadata.ASCII_STRING_MARSHALLER);

    private static final Metadata.Key<String> USERNAME_KEY =
            Metadata.Key.of("x-grpc-username", Metadata.ASCII_STRING_MARSHALLER);

    private final String secret;

    public GrpcServerAuthInterceptor(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("grpc.auth-secret must not be blank");
        }
        this.secret = secret;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        var token = headers.get(ACCESS_TOKEN_KEY);
        if (token == null || token.isBlank()) {
            log.warn("gRPC auth: missing access token");
            call.close(Status.UNAUTHENTICATED.withDescription("missing access token"), new Metadata());
            return new ServerCall.Listener<>() {
            };
        }

        if (!JwtUtils.verify(token, secret)) {
            log.warn("gRPC auth: invalid token");
            call.close(Status.UNAUTHENTICATED.withDescription("invalid access token"), new Metadata());
            return new ServerCall.Listener<>() {
            };
        }

        var username = JwtUtils.getUsername(token);
        if (username != null) {
            headers.put(USERNAME_KEY, username);
        }

        log.debug("gRPC auth: authenticated user={}", username);
        return next.startCall(call, headers);
    }
}
