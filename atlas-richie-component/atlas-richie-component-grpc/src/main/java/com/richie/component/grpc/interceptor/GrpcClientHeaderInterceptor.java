package com.richie.component.grpc.interceptor;

import com.richie.context.common.api.HeaderContextHolder;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * gRPC 客户端拦截器 — 从 {@link HeaderContextHolder} 读取头信息并写入请求 Metadata
 *
 * <p>在 gRPC 客户端发起请求时，从当前线程的 {@link HeaderContextHolder} 中读取白名单内的头信息，
 * 注入到 gRPC 请求的 {@link Metadata} 中，实现上下文透传。</p>
 *
 * @author richie696
 * @version 1.0
 * @since 1.0.0
 */
@Slf4j
public final class GrpcClientHeaderInterceptor implements ClientInterceptor {

    private final Set<String> propagatedHeaders;

    public GrpcClientHeaderInterceptor(Set<String> headers) {
        this.propagatedHeaders = Set.copyOf(headers);
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(
                next.newCall(method, callOptions)) {

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                // 从 HeaderContextHolder 读取白名单内的头信息，写入 gRPC Metadata
                if (HeaderContextHolder.isNotEmpty()) {
                    var ctx = HeaderContextHolder.getContext();
                    for (var key : propagatedHeaders) {
                        var value = ctx.get(key);
                        if (value != null) {
                            headers.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value);
                            log.trace("gRPC client interceptor: propagated header [{}]", key);
                        }
                    }
                }
                super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(responseListener) {
                }, headers);
            }
        };
    }
}
