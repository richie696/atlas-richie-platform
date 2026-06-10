package com.richie.component.grpc.interceptor;

import com.richie.context.common.api.HeaderContextHolder;
import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * gRPC 服务端拦截器 — 将请求 Metadata 中指定的头信息注入 {@link HeaderContextHolder}
 *
 * <p>在 gRPC 服务端收到请求时，从 {@link Metadata} 中提取白名单内的头信息，
 * 存入当前线程的 {@link HeaderContextHolder}，供下游业务代码（如租户解析）读取。
 * 请求结束后自动清理上下文，防止线程污染。</p>
 *
 * @author richie696
 * @version 1.0
 * @since 1.0.0
 */
@Slf4j
public final class GrpcServerHeaderInterceptor implements ServerInterceptor {

    private final Set<String> propagatedHeaders;

    public GrpcServerHeaderInterceptor(Set<String> headers) {
        this.propagatedHeaders = headers.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        // 从 Metadata 提取白名单内的头信息并注入 HeaderContextHolder
        for (var key : headers.keys()) {
            if (propagatedHeaders.contains(key)) {
                var value = headers.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER));
                if (value != null) {
                    HeaderContextHolder.setHeader(key, value);
                    log.trace("gRPC server interceptor: injected header [{}]", key);
                }
            }
        }

        var forwardCall = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
        };

        var listener = next.startCall(forwardCall, headers);

        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(listener) {
            @Override
            public void onCancel() {
                try {
                    super.onCancel();
                } finally {
                    HeaderContextHolder.removeContext();
                }
            }

            @Override
            public void onComplete() {
                try {
                    super.onComplete();
                } finally {
                    HeaderContextHolder.removeContext();
                }
            }
        };
    }
}
