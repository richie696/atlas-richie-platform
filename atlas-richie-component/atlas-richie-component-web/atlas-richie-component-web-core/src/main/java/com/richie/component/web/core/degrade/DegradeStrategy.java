package com.richie.component.web.core.degrade;

import java.util.Set;

/**
 * 降级策略 SPI（README.md §4.7）。
 * <p>
 * 业务方实现本接口，自定义降级响应；通过
 * {@link DegradeStrategyRegistry#register(String, DegradeStrategy)} 注册到注册表。
 * <p>
 * <strong>触发流程</strong>（在 {@link DegradeInterceptor#intercept} 中）：
 * <ol>
 *   <li>业务链执行后检查异常 / 高延迟 / 手动标记</li>
 *   <li>计算 {@link Trigger}</li>
 *   <li>遍历注册表，按 {@link #order()} 升序遍历，第一个
 *       {@link #matches(Trigger)} 返回 true 的策略被采用</li>
 *   <li>调 {@link #build(Trigger, java.util.Map)} 生成 {@link DegradeResult}</li>
 * </ol>
 *
 * <h2>设计原则</h2>
 * <ul>
 *   <li><strong>无 servlet 依赖</strong>：本接口只用基本类型，方便单元测试</li>
 *   <li><strong>无状态</strong>：策略实现应是线程安全的无状态对象（注册表单例持有）</li>
 *   <li><strong>可短路</strong>：业务可通过 {@code context.setAttribute("degrade.skip", "true")}
 *       跳过本轮降级（例如调试场景）</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-07
 */
public interface DegradeStrategy {

    /**
     * 策略名（在注册表中唯一）。建议采用小写中划线命名（例：{@code default-stub}）。
     */
    String name();

    /**
     * 触发条件：本策略响应哪些 {@link Trigger}。
     * <p>返回空集合等价于"永不触发"（注册但不参与匹配）。
     */
    Set<Trigger> triggers();

    /**
     * 顺序值（值越小越靠前）。建议采用：
     * <ul>
     *   <li>{@code Integer.MIN_VALUE / 2}：全局兜底（如返回缓存 / 占位）</li>
     *   <li>{@code 0}：默认</li>
     *   <li>{@code Integer.MAX_VALUE / 2}：仅作诊断用途</li>
     * </ul>
     */
    int order();

    /**
     * 是否匹配本次触发。
     *
     * @param trigger 本轮触发条件（可能为 null：表示未触发任何条件）
     * @return true 表示采用本策略
     */
    boolean matches(Trigger trigger);

    /**
     * 生成降级响应。
     *
     * @param trigger 本轮触发条件
     * @param ctx     上下文参数（key 可能包含：{@code path}, {@code clientKey},
     *                {@code exception}, {@code latencyMs} 等）
     * @return 不可变的 {@link DegradeResult}
     */
    DegradeResult build(Trigger trigger, java.util.Map<String, Object> ctx);
}