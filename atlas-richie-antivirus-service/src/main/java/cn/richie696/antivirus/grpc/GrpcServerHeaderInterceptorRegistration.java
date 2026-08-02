package cn.richie696.antivirus.grpc;

import cn.richie696.component.grpc.interceptor.GrpcServerHeaderInterceptor;
import io.grpc.Metadata;
import io.grpc.ServerInterceptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 把 atlas-richie-component-grpc 的 {@link GrpcServerHeaderInterceptor} 暴露成 gRPC server
 * 可识别的全局拦截器。
 *
 * <p>grpc-spring-boot-starter 只会自动注册标了 {@code GrpcGlobalServerInterceptor} 的
 * {@link ServerInterceptor} bean，所以这里用包装类的形式让 starter 把它装到 server 上。
 */
@GrpcGlobalServerInterceptor
@ConditionalOnProperty(prefix = "platform.antivirus.grpc", name = "enabled", havingValue = "true")
public class GrpcServerHeaderInterceptorRegistration implements ServerInterceptor {

    private final GrpcServerHeaderInterceptor delegate;

    public GrpcServerHeaderInterceptorRegistration(GrpcServerHeaderInterceptor delegate) {
        this.delegate = delegate;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        return delegate.interceptCall(call, headers, next);
    }
}