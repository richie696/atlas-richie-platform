package com.richie.component.web.core.spi.support;

import com.richie.component.web.core.spi.WebInterceptor;
import com.richie.component.web.core.spi.WebInterceptorChain;
import com.richie.component.web.core.spi.WebRequestContext;

import java.util.List;
import java.util.Objects;

/**
 * 默认 {@link WebInterceptorChain} 实现：按注册顺序驱动拦截器执行。
 * <p>
 * 适配层（{@code InterceptingFilter}）在每次请求入口构造一个实例，调用
 * {@link #proceed(WebRequestContext)} 触发链首；拦截器可在内部再次调用 {@code proceed} 触发下一个。
 *
 * <h2>并发模型</h2>
 * <p>本类<strong>非线程安全</strong>：每个请求一个实例，跨线程使用必须另行同步。
 *
 * <h2>链末行为</h2>
 * <p>{@link #proceed(WebRequestContext)} 在 index 越界时为 no-op（不抛异常）；这是为了让拦截器
 * 的末尾（如 HangDetection）调用一次 {@code proceed} 而无需先判边界。
 *
 * @author richie696
 * @since 2026-07
 */
public final class DefaultWebInterceptorChain implements WebInterceptorChain {

    private final List<WebInterceptor> interceptors;
    private int index;

    public DefaultWebInterceptorChain(List<WebInterceptor> interceptors) {
        this.interceptors = List.copyOf(Objects.requireNonNull(interceptors, "interceptors must not be null"));
    }

    @Override
    public void proceed(WebRequestContext ctx) throws Exception {
        if (ctx.isShortCircuited()) {
            // 短路后任何后续 proceed 调用均为 no-op（让拦截器在短路的 ctx 上调用 proceed 不会破坏链）
            return;
        }
        if (index >= interceptors.size()) {
            // 链末 —— 此处到达说明"业务拦截器调到链末且未短路"，进入 Spring Dispatcher
            // 适配层（web-tomcat / web-jetty）在自己的 doFilterInternal 中处理链末写回
            return;
        }
        WebInterceptor next = interceptors.get(index++);
        next.intercept(ctx, this);
    }

    @Override
    public List<WebInterceptor> interceptors() {
        return interceptors;
    }

    /**
     * 当前游标（用于诊断与测试断言）。
     */
    public int currentIndex() {
        return index;
    }
}