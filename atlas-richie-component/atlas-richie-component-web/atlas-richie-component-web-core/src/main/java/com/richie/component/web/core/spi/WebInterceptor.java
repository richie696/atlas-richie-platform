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
package com.richie.component.web.core.spi;

/**
 * 容器无关的拦截器接口（SPI 层）。
 * <p>
 * 一个 {@code WebInterceptor} 在请求生命周期某个时点执行；可读取 / 修改 {@link WebRequestContext}，
 * 也可通过 {@link WebInterceptorChain#proceed(WebRequestContext)} 驱动后续拦截器执行。
 *
 * <h2>短路语义（见 README.md §3.4）</h2>
 * <ul>
 *   <li>拦截器<strong>不应</strong>抛异常短路 —— 应通过 {@link WebRequestContext#markShortCircuit(int, String)} 标记，
 *       并<strong>不要</strong>调用 {@code chain.proceed(ctx)}。</li>
 *   <li>拦截器抛出的异常会被链外层包装为 5xx 响应，并 publish {@code RequestException} 事件。</li>
 * </ul>
 *
 * <h2>执行顺序</h2>
 * <p>由 {@link WebInterceptorChain} 决定（注册顺序即执行顺序），业务方不可改：
 * <ol>
 *   <li>{@code OtelSpanInterceptor}</li>
 *   <li>{@code RequestLifecycleHookInterceptor}</li>
 *   <li>{@code RateLimitInterceptor}</li>
 *   <li>{@code CircuitBreakerInterceptor}</li>
 *   <li>{@code HangDetectionInterceptor}</li>
 * </ol>
 *
 * <h2>实现要求</h2>
 * <ul>
 *   <li>线程安全：拦截器可能在多线程（异步 / VT）下并发调用，所有可变状态必须并发安全。</li>
 *   <li>无状态优先：尽量 stateless，让 Spring singleton bean 直接持有。</li>
 *   <li>不可在 {@code intercept} 中启动线程或阻塞 IO —— 由 Hang Detection 异步负责。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-07
 */
@FunctionalInterface
public interface WebInterceptor {

    /**
     * 拦截器入口。
     *
     * @param ctx   当前请求上下文
     * @param chain 拦截器链，调用 {@link WebInterceptorChain#proceed(WebRequestContext)} 触发下一个拦截器；
     *              业务方选择不调用即代表短路
     * @throws Exception 实现可抛任何异常；链外层将其转换为 5xx 响应
     */
    void intercept(WebRequestContext ctx, WebInterceptorChain chain) throws Exception;
}