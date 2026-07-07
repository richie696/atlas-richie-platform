package com.richie.component.web.core.degrade;

/**
 * 降级触发条件（README.md §4.7）。
 * <p>
 * 区分语义：
 * <ul>
 *   <li>{@link #EXCEPTION}：业务方法抛出异常时触发（默认）</li>
 *   <li>{@link #HIGH_LATENCY}：执行耗时超阈值时触发（暂未实现检测，由业务层标记）</li>
 *   <li>{@link #CUSTOM}：业务自定义条件（业务在 request attribute 标记
 *       {@code degrade.manual=true}）</li>
 * </ul>
 *
 * <p><strong>非交互性触发条件</strong>：本枚举只表示"是否可能触发降级"，
 * 是否真的降级由 {@link DegradeStrategy#matches(Trigger)} 决定。
 *
 * @author richie696
 * @since 2026-07
 */
public enum Trigger {
    /**
     * 业务方法抛出异常（任何 Throwable）。
     */
    EXCEPTION,
    /**
     * 高延迟（业务层标记 lat 超阈值，策略层判定）。
     */
    HIGH_LATENCY,
    /**
     * 业务自定义触发（request attribute {@code degrade.manual=true}）。
     */
    CUSTOM
}