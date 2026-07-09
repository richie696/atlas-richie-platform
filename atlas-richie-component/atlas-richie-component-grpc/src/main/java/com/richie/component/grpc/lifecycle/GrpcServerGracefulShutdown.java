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
package com.richie.component.grpc.lifecycle;

import io.grpc.Server;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * gRPC 服务端优雅停服
 *
 * <p>监听 Spring {@link ContextClosedEvent}，按顺序关闭所有已注册的 gRPC Server。
 * 先调用 {@link Server#shutdown()} 拒绝新请求并等待在途请求完成，
 * 超时后调用 {@link Server#shutdownNow()} 强制关闭。</p>
 *
 * <p>使用方式：应用代码在创建 gRPC Server 后调用 {@link #register(Server)} 注册。</p>
 *
 * @author richie696
 * @version 1.0
 * @since 1.0.0
 */
@Slf4j
public class GrpcServerGracefulShutdown implements ApplicationListener<ContextClosedEvent> {

    private final List<Server> servers = new ArrayList<>();
    private final Duration timeout;

    public GrpcServerGracefulShutdown(Duration timeout) {
        this.timeout = timeout;
    }

    /**
     * 注册一个 gRPC Server 实例，在 Spring 容器关闭时自动执行优雅停服
     *
     * @param server gRPC Server 实例
     */
    public void register(Server server) {
        if (server != null && !server.isShutdown()) {
            servers.add(server);
            log.info("gRPC server registered for graceful shutdown on port {}", server.getPort());
        }
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        log.info("Graceful shutdown initiated for {} gRPC server(s), timeout={}s", servers.size(), timeout.toSeconds());

        for (var server : servers) {
            var port = server.getPort();
            if (server.isShutdown()) {
                continue;
            }
            server.shutdown();
            try {
                if (!server.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    log.warn("gRPC server on port {} did not terminate within timeout, forcing shutdown", port);
                    server.shutdownNow();
                }
                log.info("gRPC server on port {} shut down", port);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                server.shutdownNow();
                log.warn("gRPC server on port {} shutdown interrupted", port);
            }
        }
    }
}
