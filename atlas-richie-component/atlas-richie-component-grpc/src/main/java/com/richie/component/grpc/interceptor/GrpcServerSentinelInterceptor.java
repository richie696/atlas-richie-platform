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

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;

/**
 * gRPC 服务端 Sentinel 拦截器 — 限流 / 熔断 / 降级
 *
 * <p>基于 Sentinel 对每次 gRPC 调用进行流量控制和熔断保护。
 * 资源名使用 gRPC 方法全名（如 {@code com.example.Service/getUser}）。</p>
 *
 * <p>三种防护策略：</p>
 * <ul>
 *   <li><b>限流</b>：超出 QPS/并发线程数阈值 → {@code RESOURCE_EXHAUSTED}</li>
 *   <li><b>熔断</b>：错误率或慢调用比例超过阈值 → {@code UNAVAILABLE}</li>
 *   <li><b>降级计数</b>：调用 {@link Tracer#trace(Throwable)} 让 Sentinel 统计异常，驱动熔断决策</li>
 * </ul>
 *
 * <p>推荐使用 Nacos 动态下发规则，规则示例：</p>
 * <pre>{@code
 * // 限流规则
 * {"resource":"com.example.Service/getUser","grade":1,"count":100}
 * // 熔断规则
 * {"resource":"com.example.Service/getUser","grade":0,"count":10,"timeWindow":10}
 * }</pre>
 *
 * <p>该拦截器应注册在调用链靠前位置（Metrics 之前），以准确统计被拦截的请求。</p>
 *
 * @author richie696
 * @version 1.0
 * @since 1.0.0
 */
@Slf4j
public final class GrpcServerSentinelInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        var method = call.getMethodDescriptor().getFullMethodName();
        Entry entry = null;

        try {
            entry = SphU.entry(method, EntryType.IN);
        } catch (FlowException e) {
            log.warn("gRPC flow limited: method={}, rule={}", method, e.getRule().getResource());
            call.close(Status.RESOURCE_EXHAUSTED.withDescription("rate limited"), new Metadata());
            return new ServerCall.Listener<>() {
            };
        } catch (DegradeException e) {
            log.warn("gRPC circuit broken: method={}, rule={}", method, e.getRule().getResource());
            call.close(Status.UNAVAILABLE.withDescription("degraded"), new Metadata());
            return new ServerCall.Listener<>() {
            };
        } catch (BlockException e) {
            log.warn("gRPC blocked: method={}, type={}", method, e.getClass().getSimpleName());
            call.close(Status.RESOURCE_EXHAUSTED.withDescription("blocked by sentinel"), new Metadata());
            return new ServerCall.Listener<>() {
            };
        }

        final Entry sentinelEntry = entry;

        var forwardCall = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
        };

        var listener = next.startCall(forwardCall, headers);

        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(listener) {
            @Override
            public void onHalfClose() {
                try {
                    super.onHalfClose();
                } catch (Exception e) {
                    Tracer.trace(e);
                    throw e;
                }
            }

            @Override
            public void onMessage(ReqT message) {
                try {
                    super.onMessage(message);
                } catch (Exception e) {
                    Tracer.trace(e);
                    throw e;
                }
            }

            @Override
            public void onCancel() {
                try {
                    super.onCancel();
                } finally {
                    if (sentinelEntry != null) {
                        sentinelEntry.exit();
                    }
                }
            }

            @Override
            public void onComplete() {
                try {
                    super.onComplete();
                } finally {
                    if (sentinelEntry != null) {
                        sentinelEntry.exit();
                    }
                }
            }
        };
    }
}
