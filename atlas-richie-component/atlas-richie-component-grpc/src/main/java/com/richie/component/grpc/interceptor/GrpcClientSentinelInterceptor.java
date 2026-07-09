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
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;

/**
 * gRPC 客户端 Sentinel 拦截器 — 限流 / 熔断 / 降级
 *
 * <p>对出站 gRPC 调用进行流量控制，与服务端 {@link GrpcServerSentinelInterceptor} 对称。
 * 资源名使用 gRPC 方法全名（如 {@code com.example.Service/getUser}）。</p>
 *
 * <p>三种防护策略：</p>
 * <ul>
 *   <li><b>限流</b>：超出 QPS 阈值 → 抛出 {@code RuntimeException("rate limited")}</li>
 *   <li><b>熔断</b>：下游错误率过高 → 抛出 {@code RuntimeException("degraded")}</li>
 *   <li><b>降级计数</b>：调用 {@link Tracer#trace(Throwable)} 让 Sentinel 统计异常，驱动熔断决策</li>
 * </ul>
 *
 * @author richie696
 * @since 1.0
 */
@Slf4j
public final class GrpcClientSentinelInterceptor implements ClientInterceptor {

    /**
     * 拦截客户端 gRPC 调用，通过 Sentinel Entry 进行流量控制
     *
     * <p>使用 {@link SphU#entry(String, EntryType)} 以 {@code OUT} 类型申请资源：
     * 在规定 QPS 内放行，超出则阻断并抛出异常供上游降级处理。</p>
     *
     * <p>响应监听器在 {@code onClose} 中：
     * 非 OK 状态调用 {@link Tracer#trace(Throwable)} 驱动 Sentinel 熔断器统计，
     * 无论成功失败都通过 {@code entry.exit()} 释放资源。</p>
     *
     * @param method      RPC 方法描述符
     * @param callOptions 调用选项
     * @param next        下一个 Channel
     * @param <ReqT>      请求类型
     * @param <RespT>     响应类型
     * @return 包装后的 ClientCall
     * @throws RuntimeException 当被 Sentinel 限流或熔断时抛出，上游应捕获后降级
     */
    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        var methodName = method.getFullMethodName();
        Entry entry = null;

        // 尝试获取 Sentinel 资源许可，被限流/熔断时抛出异常
        try {
            entry = SphU.entry(methodName, EntryType.OUT);
        } catch (FlowException e) {
            log.warn("gRPC client flow limited: method={}", methodName);
            throw new RuntimeException("gRPC client call rate limited: " + methodName, e);
        } catch (DegradeException e) {
            log.warn("gRPC client circuit broken: method={}", methodName);
            throw new RuntimeException("gRPC client call degraded: " + methodName, e);
        } catch (BlockException e) {
            log.warn("gRPC client blocked: method={}, type={}", methodName, e.getClass().getSimpleName());
            throw new RuntimeException("gRPC client call blocked: " + methodName, e);
        }

        final Entry sentinelEntry = entry;
        var clientCall = next.newCall(method, callOptions);

        return new ForwardingClientCall.SimpleForwardingClientCall<>(clientCall) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                var tracedListener = new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(responseListener) {
                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        try {
                            // 非 OK 状态通知 Sentinel，驱动熔断器统计
                            if (!status.isOk()) {
                                Tracer.trace(new RuntimeException(
                                        "gRPC call failed: " + methodName + " status=" + status.getCode()));
                            }
                            super.onClose(status, trailers);
                        } finally {
                            // 释放 Sentinel Entry 资源
                            if (sentinelEntry != null) {
                                sentinelEntry.exit();
                            }
                        }
                    }
                };
                super.start(tracedListener, headers);
            }
        };
    }
}
