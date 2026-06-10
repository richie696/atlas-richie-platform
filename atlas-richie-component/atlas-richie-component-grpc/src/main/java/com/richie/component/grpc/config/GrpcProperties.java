package com.richie.component.grpc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * gRPC 组件配置属性
 *
 * @author richie696
 * @version 1.0
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "richie.grpc")
public class GrpcProperties {

    private Server server = new Server();
    private Client client = new Client();
    private HeaderPropagation headerPropagation = new HeaderPropagation();

    @Data
    public static class Server {
        private boolean metricsEnabled = true;
        private boolean loggingEnabled = true;
        private boolean exceptionMappingEnabled = true;
        private boolean authEnabled = false;
        private String authSecret;
        private Duration gracefulShutdownTimeout = Duration.ofSeconds(30);

        private boolean tracingEnabled = true;
        private Duration keepAliveTime = Duration.ofSeconds(30);
        private Duration keepAliveTimeout = Duration.ofSeconds(10);
        private boolean permitKeepAliveWithoutCalls = true;
    }

    @Data
    public static class Client {
        private boolean metricsEnabled = true;
        private boolean loggingEnabled = true;
        private boolean tracingEnabled = true;

        private Duration keepAliveTime = Duration.ofSeconds(30);
        private Duration keepAliveTimeout = Duration.ofSeconds(10);
        private boolean keepAliveWithoutCalls = true;
    }

    @Data
    public static class HeaderPropagation {
        private boolean enabled = true;
        private Set<String> headers = new HashSet<>(Set.of(
                "x-rd-request-apitoken",
                "x-tenant-id"
        ));
    }
}
