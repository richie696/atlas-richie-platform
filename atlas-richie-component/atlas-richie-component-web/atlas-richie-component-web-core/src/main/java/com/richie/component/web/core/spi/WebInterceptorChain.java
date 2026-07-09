/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.web.core.spi;

import java.util.List;

/**
 * 拦截器链（SPI 层）。
 * <p>
 * 由适配层（{@code InterceptingFilter}）在请求入口构造，持有本请求的拦截器列表与执行游标；
 * 业务拦截器通过 {@link #proceed(WebRequestContext)} 驱动下一个拦截器执行。
 *
 * <h2>驱动模型</h2>
 * <pre>
 *   适配层拦截 → newChain(interceptors)
 *            ↓
 *   chain.proceed(ctx)  // index=0
 *            ↓
 *   interceptors[0].intercept(ctx, chain)
 *            ↓ (业务方选择调用)
 *   chain.proceed(ctx)  // index=1
 *            ↓
 *   interceptors[1].intercept(ctx, chain)
 *            ↓
 *   ...
 *            ↓
 *   index == size → 链末 → 适配层 chain.doFilter(req,resp)  // 进入 Spring Dispatcher
 * </pre>
 *
 * <h2>短路（§3.4）</h2>
 * <p>当拦截器调完业务逻辑后<strong>不</strong>调用 {@link #proceed(WebRequestContext)} 时，
 * 链的执行在该拦截器处终止；适配层根据 {@link WebRequestContext#isShortCircuited()} 决定是否走
 * {@code chain.doFilter}。
 *
 * <h2>异常传播</h2>
 * <p>{@link #proceed(WebRequestContext)} 抛出的异常会沿调用栈向上传递至触发它的拦截器；
 * 业务方可在该拦截器中捕获并处理，或继续抛出由链外层兜底。
 *
 * @author richie696
 * @since 2026-07
 */
public interface WebInterceptorChain {

    /**
     * 驱动下一个拦截器执行；已到链末则调用本方法相当于 no-op（不应发生，调用方应在调到链末前退出）。
     *
     * @param ctx 当前请求上下文
     * @throws Exception 下一个拦截器或链末执行过程中抛出的异常
     */
    void proceed(WebRequestContext ctx) throws Exception;

    /**
     * 当前链持有的全部拦截器（按注册顺序），用于诊断 / 日志 / 测试断言。
     * <p>返回只读视图。
     */
    List<WebInterceptor> interceptors();
}