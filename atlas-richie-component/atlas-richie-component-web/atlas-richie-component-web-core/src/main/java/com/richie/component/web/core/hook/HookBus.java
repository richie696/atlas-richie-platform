package com.richie.component.web.core.hook;

import java.util.List;

/**
 * 事件总线（README.md §4.5 HookBus）。
 * <p>
 * publish/subscribe 模型：拦截器或 HookBus.publish 调用方调 {@link #publish} 派发事件，
 * 订阅者通过 {@link #subscribe} 注册。实现需保证：
 * <ul>
 *   <li>多订阅者 fan-out（按注册顺序）</li>
 *   <li>一个订阅者抛异常不影响其他订阅者</li>
 *   <li>无订阅者的事件静默丢弃</li>
 * </ul>
 *
 * <h2>sync vs async</h2>
 * <p>实现可同步发布（与调用方同线程）或异步（提交到 Executor）。
 * {@link DefaultHookBus} 同步实现——简单且可预测，订阅者应自己异步化（如 Micrometer 注册）。
 *
 * @author richie696
 * @since 2026-07
 */
public interface HookBus {

    /**
     * 注册订阅者。订阅者将接收所有匹配事件类型的 publish。
     *
     * @param eventType 事件 class（如 {@code RequestCompletedEvent.class}）
     * @param subscriber 订阅者实例
     * @param <E> 事件类型
     * @return 用于反注册的 token
     */
    <E extends HookEvent> Subscription subscribe(Class<E> eventType, HookSubscriber<E> subscriber);

    /**
     * 派发事件。HookBus 内部 fan-out 给所有该事件类型的订阅者。
     * 订阅者抛的异常被 HookBus 隔离（不影响其他订阅者）。
     */
    void publish(HookEvent event);

    /**
     * 当前各事件类型的订阅者数（按事件类型）。主要用于诊断 / 测试。
     */
    List<String> diagnosticView();

    /**
     * 订阅反注册 token。
     */
    @FunctionalInterface
    interface Subscription extends AutoCloseable {
        @Override
        void close();
    }
}