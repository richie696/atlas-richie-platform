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
package com.richie.component.grpc.interceptor;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicReference;

/**
 * gRPC 客户端日志拦截器
 *
 * <p>记录每次 gRPC 客户端调用的方法名、状态码和耗时，与服务端 {@link GrpcServerLoggingInterceptor} 对称。
 * 耗时测量从调用发起开始，到收到服务端响应结束，精确反映完整 RPC 耗时。</p>
 *
 * <p>日志格式：</p>
 * <pre>{@code
 * gRPC client call completed: method=com.example.Service/getUser, status=OK, duration=12ms
 * }</pre>
 *
 * @author richie696
 * @since 1.0
 */
@Slf4j
public final class GrpcClientLoggingInterceptor implements ClientInterceptor {

    /**
     * 拦截客户端 gRPC 调用，包装响应监听器记录日志
     *
     * <p>通过 {@link ForwardingClientCall} 和 {@link ForwardingClientCallListener} 双重代理：
     * 在请求发送前记录开始时间，在收到服务端 close 响应时计算耗时并输出日志。</p>
     *
     * @param method      RPC 方法描述符
     * @param callOptions 调用选项（超时、deadline 等）
     * @param next        下一个 Channel（实际的网络连接或下一个拦截器）
     * @param <ReqT>      请求类型
     * @param <RespT>     响应类型
     * @return 包装后的 ClientCall，在 start 阶段注入日志监听器
     */
    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        var methodName = method.getFullMethodName();
        var startNanos = System.nanoTime();
        var statusRef = new AtomicReference<Status>();

        log.debug("gRPC client call started: method={}", methodName);

        // 通过 next Channel 创建实际的客户端调用
        var clientCall = next.newCall(method, callOptions);

        return new ForwardingClientCall.SimpleForwardingClientCall<>(clientCall) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                // 包装响应监听器，在 onClose 时记录耗时和状态
                var tracedListener = new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(responseListener) {
                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        var durationMs = (System.nanoTime() - startNanos) / 1_000_000;
                        if (status.isOk()) {
                            log.info("gRPC client call completed: method={}, status=OK, duration={}ms",
                                    methodName, durationMs);
                        } else {
                            log.warn("gRPC client call failed: method={}, status={}, duration={}ms, description={}",
                                    methodName, status.getCode(), durationMs, status.getDescription());
                        }
                        super.onClose(status, trailers);
                    }
                };
                super.start(tracedListener, headers);
            }
        };
    }
}
