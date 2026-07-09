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

import java.util.concurrent.TimeoutException;

/**
 * gRPC 服务端异常映射拦截器
 *
 * <p>捕获 Handler 层抛出的未处理异常，按标准规则映射为 gRPC {@link Status}，避免线程崩溃。
 * 映射后关闭调用并向 Metrics/Logging 拦截器透出正确的状态码。</p>
 *
 * <p>默认映射规则：</p>
 * <ul>
 *   <li>{@link IllegalArgumentException} → {@code INVALID_ARGUMENT}</li>
 *   <li>{@link IllegalStateException} → {@code FAILED_PRECONDITION}</li>
 *   <li>{@link UnsupportedOperationException} → {@code UNIMPLEMENTED}</li>
 *   <li>{@link TimeoutException} → {@code DEADLINE_EXCEEDED}</li>
 *   <li>其他 {@link RuntimeException} → {@code INTERNAL}</li>
 * </ul>
 *
 * <p>该拦截器必须最靠近 Handler（注册链中第一个执行），以捕获业务逻辑中的所有异常。</p>
 *
 * @author richie696
 * @version 1.0
 * @since 1.0.0
 */
@Slf4j
public final class GrpcServerExceptionInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        var forwardCall = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
        };
        ServerCall.Listener<ReqT> listener;

        try {
            listener = next.startCall(forwardCall, headers);
        } catch (Exception e) {
            var status = mapException(e);
            log.error("gRPC handler exception mapped to status={}", status.getCode(), e);
            call.close(status, new Metadata());
            return new ServerCall.Listener<>() {
            };
        }

        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(listener) {
            @Override
            public void onHalfClose() {
                try {
                    super.onHalfClose();
                } catch (Exception e) {
                    var status = mapException(e);
                    log.error("gRPC onHalfClose exception mapped to status={}", status.getCode(), e);
                    call.close(status, new Metadata());
                }
            }

            @Override
            public void onMessage(ReqT message) {
                try {
                    super.onMessage(message);
                } catch (Exception e) {
                    var status = mapException(e);
                    log.error("gRPC onMessage exception mapped to status={}", status.getCode(), e);
                    call.close(status, new Metadata());
                }
            }

            @Override
            public void onReady() {
                try {
                    super.onReady();
                } catch (Exception e) {
                    var status = mapException(e);
                    log.error("gRPC onReady exception mapped to status={}", status.getCode(), e);
                    call.close(status, new Metadata());
                }
            }
        };
    }

    private Status mapException(Exception e) {
        if (e instanceof IllegalArgumentException) {
            return Status.INVALID_ARGUMENT.withDescription(e.getMessage());
        }
        if (e instanceof IllegalStateException) {
            return Status.FAILED_PRECONDITION.withDescription(e.getMessage());
        }
        if (e instanceof UnsupportedOperationException) {
            return Status.UNIMPLEMENTED.withDescription(e.getMessage());
        }
        if (e instanceof TimeoutException) {
            return Status.DEADLINE_EXCEEDED.withDescription(e.getMessage());
        }
        return Status.INTERNAL.withDescription(e.getMessage());
    }
}
