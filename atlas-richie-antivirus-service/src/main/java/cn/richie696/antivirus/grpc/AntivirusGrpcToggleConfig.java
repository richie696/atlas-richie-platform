package cn.richie696.antivirus.grpc;

import cn.richie696.component.grpc.interceptor.GrpcServerHeaderInterceptor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * 用 platform.antivirus.grpc.enabled 控制 gRPC server 生命周期，并把头信息拦截器
 * （来自 {@code atlas-richie-component-grpc}）注册到 server 上。
 *
 * <p>grpc-spring-boot-starter 默认无条件启动 gRPC server；这里覆盖其 {@code GrpcServerLifecycle} bean
 * 并按需启用或禁用。</p>
 */
@Slf4j
@Configuration
public class AntivirusGrpcToggleConfig {

    /**
     * 启用时接管 gRPC server 生命周期；启动后由 grpc-spring-boot-starter 提供的
     * {@link net.devh.boot.grpc.server.serverfactory.GrpcServerFactory} 构建并启动 server，关闭时优雅停止。
     */
    @Bean(destroyMethod = "stop")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "platform.antivirus.grpc", name = "enabled", havingValue = "true")
    public SmartLifecycle antivirusGrpcServerLifecycle(
            net.devh.boot.grpc.server.serverfactory.GrpcServerFactory factory,
            cn.richie696.antivirus.config.AntivirusProperties properties) {
        return new ActiveGrpcServerLifecycle(factory, properties.getGrpc().getPort());
    }

    /**
     * 禁用时提供占位生命周期，阻止 starter 启动 gRPC server。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "platform.antivirus.grpc", name = "enabled", havingValue = "false", matchIfMissing = true)
    public SmartLifecycle antivirusDisabledGrpcServerLifecycle() {
        log.info("gRPC server disabled by platform.antivirus.grpc.enabled=false; " +
                "REST endpoints remain the only entry point");
        return new InactiveGrpcServerLifecycle();
    }

    /**
     * 把 atlas-richie-component-grpc 的 header 拦截器包装成 {@code @GrpcGlobalInterceptor}，
     * 让 grpc-spring-boot-starter 把它注册到 server。这样 gRPC 调用方的 X-Tenant-Id metadata
     * 会通过 HeaderContextHolder 注入，让 service 层与 REST 共享同一个租户上下文。
     */
    @Bean
    @ConditionalOnBean(GrpcServerHeaderInterceptor.class)
    @ConditionalOnProperty(prefix = "platform.antivirus.grpc", name = "enabled", havingValue = "true")
    public GrpcServerHeaderInterceptorRegistration antivirusGrpcHeaderRegistration(
            GrpcServerHeaderInterceptor headerInterceptor) {
        return new GrpcServerHeaderInterceptorRegistration(headerInterceptor);
    }

    private static final class ActiveGrpcServerLifecycle implements SmartLifecycle {
        private final net.devh.boot.grpc.server.serverfactory.GrpcServerFactory factory;
        private final int port;
        private volatile io.grpc.Server server;

        ActiveGrpcServerLifecycle(net.devh.boot.grpc.server.serverfactory.GrpcServerFactory factory, int port) {
            this.factory = factory;
            this.port = port;
        }

        @Override
        public void start() {
            try {
                this.server = factory.createServer();
                this.server.start();
                log.info("AntivirusService gRPC server started on port {}", port);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to start antivirus gRPC server", e);
            }
        }

        @Override
        public void stop() {
            if (server != null) {
                server.shutdown();
            }
        }

        @Override
        public boolean isRunning() {
            return server != null && !server.isShutdown();
        }

        @Override
        public boolean isAutoStartup() {
            return true;
        }

        @Override
        public int getPhase() {
            return Integer.MAX_VALUE - 1000;
        }
    }

    private static final class InactiveGrpcServerLifecycle implements SmartLifecycle {
        @Override public void start() {}
        @Override public void stop() {}
        @Override public boolean isRunning() { return false; }
        @Override public boolean isAutoStartup() { return true; }
        @Override public int getPhase() { return Integer.MAX_VALUE - 1000; }
    }
}