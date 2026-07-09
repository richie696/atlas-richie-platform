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
@ConfigurationProperties(prefix = "platform.grpc")
public class GrpcProperties {

    private Server server = new Server();
    private Client client = new Client();
    private HeaderPropagation headerPropagation = new HeaderPropagation();

    @Data
    public static class Server {
        /**
         * 是否启用服务端头信息透传拦截器
         */
        private boolean headerEnabled = true;
        /**
         * 是否启用服务端日志拦截器
         */
        private boolean loggingEnabled = true;
        /**
         * 是否启用服务端异常映射拦截器
         */
        private boolean exceptionMappingEnabled = true;
        /**
         * 是否启用服务端鉴权拦截器
         */
        private boolean authEnabled = false;
        /**
         * JWT 鉴权密钥
         */
        private String authSecret;
        /**
         * 是否启用服务端 Sentinel 限流拦截器
         */
        private boolean sentinelEnabled = true;
        /**
         * 是否启用服务端链路追踪拦截器
         */
        private boolean tracingEnabled = true;
        /**
         * 是否启用服务端指标采集拦截器
         */
        private boolean metricsEnabled = true;
        /**
         * 优雅停机超时时间
         */
        private Duration gracefulShutdownTimeout = Duration.ofSeconds(30);
        /**
         * 客户端 KeepAlive 时间
         */
        private Duration keepAliveTime = Duration.ofSeconds(30);
        /**
         * 客户端 KeepAlive 超时时间
         */
        private Duration keepAliveTimeout = Duration.ofSeconds(10);
        /**
         * 是否允许没有调用时保持连接
         */
        private boolean permitKeepAliveWithoutCalls = true;
    }

    @Data
    public static class Client {
        /**
         * 是否启用客户端头信息透传拦截器
         */
        private boolean headerEnabled = true;
        /**
         * 是否启用客户端日志拦截器
         */
        private boolean loggingEnabled = true;
        /**
         * 是否启用客户端链路追踪拦截器
         */
        private boolean tracingEnabled = true;
        /**
         * 是否启用客户端指标采集拦截器
         */
        private boolean metricsEnabled = true;
        /**
         * 是否启用客户端 Sentinel 限流拦截器
         */
        private boolean sentinelEnabled = true;
        /**
         * 客户端 KeepAlive 时间
         */
        private Duration keepAliveTime = Duration.ofSeconds(30);
        /**
         * 客户端 KeepAlive 超时时间
         */
        private Duration keepAliveTimeout = Duration.ofSeconds(10);
        /**
         * 是否允许没有调用时保持连接
         */
        private boolean keepAliveWithoutCalls = true;
    }

    @Data
    public static class HeaderPropagation {
        /**
         * 是否启用头信息透传
         */
        private boolean enabled = true;
        /**
         * 需要透传的头信息白名单
         */
        private Set<String> headers = new HashSet<>(Set.of(
                "x-rd-request-apitoken",
                "x-tenant-id"
        ));
    }
}
