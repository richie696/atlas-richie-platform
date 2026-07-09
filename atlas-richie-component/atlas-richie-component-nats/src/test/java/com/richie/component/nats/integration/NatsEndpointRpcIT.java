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
package com.richie.component.nats.integration;

import com.richie.component.nats.bus.NatsBus;
import com.richie.component.nats.bus.NatsEndpoint;
import com.richie.component.nats.config.NatsAutoConfiguration;
import com.richie.component.nats.exception.NatsRpcException;
import com.richie.component.nats.support.NatsIntegrationTestSupport;
import com.richie.component.nats.support.TestEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * NatsEndpoint 端到端集成测试 — 基于 {@code @Autowired NatsEndpoint} 验证 RPC 服务端注册全链路。
 *
 * <p>使用 Testcontainers 启动真实 NATS 容器，验证场景：</p>
 * <ul>
 *   <li>registerHandler + NatsBus.request() 同步 RPC</li>
 *   <li>registerHandler + NatsBus.requestAsync() 异步 RPC</li>
 *   <li>响应对象类型化(POJO 反序列化)</li>
 *   <li>Queue Group 负载均衡(同一请求只命中一个 handler)</li>
 *   <li>handler 抛异常时返回 ErrorResponse</li>
 *   <li>NoResponders / Timeout 错误</li>
 * </ul>
 *
 * @author richie696
 */
@SpringBootTest(classes = NatsAutoConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIf("com.richie.component.nats.support.NatsIntegrationTestSupport#isEnabled")
class NatsEndpointRpcIT {

    @DynamicPropertySource
    static void natsProperties(DynamicPropertyRegistry registry) {
        NatsIntegrationTestSupport.getInstance().registerNatsProperties(registry);
    }

    @Autowired
    private NatsBus natsBus;

    @Autowired
    private NatsEndpoint natsEndpoint;

    private final AtomicInteger registrationCount = new AtomicInteger();

    @AfterEach
    void cleanup() {
        // 注:registerHandler 创建的 Dispatcher/Subscription 与 Spring 生命周期绑定,
        // 测试结束后随 @DirtiesContext 销毁,无需手动清理
        registrationCount.set(0);
    }

    @Test
    void requestReplySynchronousViaEndpointHandler() {
        String subject = "rpc.sync." + UUID.randomUUID();
        natsEndpoint.registerHandler(subject, TestEvent.class,
                req -> new TestEvent("echo:" + req.content()));

        TestEvent response = natsBus.request(
                subject, new TestEvent("ping"), TestEvent.class, Duration.ofSeconds(5));
        assertThat(response.content()).isEqualTo("echo:ping");
    }

    @Test
    void requestReplyAsynchronousViaEndpointHandler() throws Exception {
        String subject = "rpc.async." + UUID.randomUUID();
        natsEndpoint.registerHandler(subject, MultiplyRequest.class,
                req -> new MultiplyResponse(req.a() * req.b()));

        CompletableFuture<MultiplyResponse> future = natsBus.requestAsync(
                subject, new MultiplyRequest(6, 7), MultiplyResponse.class, Duration.ofSeconds(5));
        assertThat(future.get(5, TimeUnit.SECONDS).result()).isEqualTo(42);
    }

    @Test
    void handlerReturnsTypedPojo() {
        String subject = "rpc.pojo." + UUID.randomUUID();
        natsEndpoint.registerHandler(subject, OrderRequest.class, req ->
                new OrderResponse("ORD-" + req.id(), req.amount() * 2));

        OrderResponse response = natsBus.request(
                subject, new OrderRequest("1001", 100.0),
                OrderResponse.class, Duration.ofSeconds(5));

        assertThat(response.orderId()).isEqualTo("ORD-1001");
        assertThat(response.total()).isEqualTo(200.0);
    }

    @Test
    void queueGroupDispatchesToSingleHandlerOnly() {
        String subject = "rpc.queue." + UUID.randomUUID();
        String queueGroup = "rpc-workers";

        // 同一 Queue Group 注册两次 handler — NATS 保证每个请求只投递给其中一个
        natsEndpoint.registerHandler(subject, queueGroup, TestEvent.class, req -> {
            registrationCount.incrementAndGet();
            return new TestEvent("A:" + req.content());
        });
        natsEndpoint.registerHandler(subject, queueGroup, TestEvent.class, req -> {
            registrationCount.incrementAndGet();
            return new TestEvent("B:" + req.content());
        });

        int totalRequests = 6;
        for (int i = 0; i < totalRequests; i++) {
            TestEvent response = natsBus.request(
                    subject, new TestEvent("req-" + i), TestEvent.class, Duration.ofSeconds(5));
            // 响应只可能来自 A: 或 B:,格式固定
            assertThat(response.content()).matches("[AB]:req-\\d+");
        }
        // total invocations == total requests
        assertThat(registrationCount.get()).isEqualTo(totalRequests);
    }

    @Test
    void handlerExceptionReturnsErrorResponse() {
        String subject = "rpc.error." + UUID.randomUUID();
        natsEndpoint.registerHandler(subject, TestEvent.class, req -> {
            throw new IllegalStateException("intentional failure for: " + req.content());
        });

        NatsEndpoint.ErrorResponse error = natsBus.request(
                subject, new TestEvent("boom"),
                NatsEndpoint.ErrorResponse.class, Duration.ofSeconds(5));

        assertThat(error).isNotNull();
        assertThat(error.error()).contains("intentional failure for: boom");
    }

    @Test
    void requestToUnregisteredSubjectThrowsNoResponders() {
        String subject = "rpc.nobody." + UUID.randomUUID();

        assertThatThrownBy(() ->
                natsBus.request(subject, new TestEvent("hello"),
                        TestEvent.class, Duration.ofSeconds(2)))
                .isInstanceOf(NatsRpcException.class)
                .matches(t -> ((NatsRpcException) t).isNoResponders()
                        || ((NatsRpcException) t).isTimeout());
    }

    @Test
    void requestTimeoutWhenHandlerIsSlow() throws Exception {
        String subject = "rpc.slow." + UUID.randomUUID();
        CountDownLatch handlerStarted = new CountDownLatch(1);
        AtomicReference<CountDownLatch> handlerCanReturn =
                new AtomicReference<>(new CountDownLatch(1));

        natsEndpoint.registerHandler(subject, TestEvent.class, req -> {
            handlerStarted.countDown();
            try {
                handlerCanReturn.get().await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new TestEvent("too-late");
        });

        assertThatThrownBy(() ->
                natsBus.request(subject, new TestEvent("slow"),
                        TestEvent.class, Duration.ofMillis(500)))
                .isInstanceOf(NatsRpcException.class)
                .matches(t -> ((NatsRpcException) t).isTimeout()
                        || ((NatsRpcException) t).isNoResponders());

        // 释放 handler,避免线程泄漏
        handlerCanReturn.get().countDown();
    }

    // ===== 测试用 POJO =====

    public record OrderRequest(String id, double amount) {
    }

    public record OrderResponse(String orderId, double total) {
    }

    public record MultiplyRequest(int a, int b) {
    }

    public record MultiplyResponse(int result) {
    }
}
