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
package com.richie.component.grpc.interceptor;

import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;

/**
 * gRPC 服务端日志拦截器
 *
 * <p>记录每次 gRPC 调用的方法名、状态码和耗时，便于故障排查与审计。</p>
 *
 * <p>日志格式（INFO 级别）：</p>
 * <pre>{@code
 * gRPC call completed: method=com.example.Service/GetUser, status=OK, duration=12ms
 * }</pre>
 *
 * <p>推荐注册顺序为靠近 Handler 的位置，以准确反映业务层耗时。</p>
 *
 * @author richie696
 * @version 1.0
 * @since 1.0.0
 */
@Slf4j
public final class GrpcServerLoggingInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        var method = call.getMethodDescriptor().getFullMethodName();
        var startNanos = System.nanoTime();

        log.debug("gRPC call started: method={}", method);

        var forwardCall = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
            @Override
            public void close(Status status, Metadata trailers) {
                var durationMs = (System.nanoTime() - startNanos) / 1_000_000;
                if (status.isOk()) {
                    log.info("gRPC call completed: method={}, status=OK, duration={}ms", method, durationMs);
                } else {
                    log.warn("gRPC call failed: method={}, status={}, duration={}ms, description={}",
                            method, status.getCode(), durationMs, status.getDescription());
                }
                super.close(status, trailers);
            }
        };

        return next.startCall(forwardCall, headers);
    }
}
