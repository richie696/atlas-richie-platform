package com.richie.component.tenant.context;

import com.richie.component.tenant.exception.BusinessException;
import com.richie.component.tenant.exception.TenantSwitchInTransactionException;
import com.richie.contract.model.TenantPrincipal;
import lombok.Setter;

import java.util.function.Supplier;

/**
 * 租户上下文静态门面。
 *
 * <p>所有外部代码统一通过此类访问租户上下文，禁止直接操作 {@link TenantContextHolder} 实现。
 * 内部委托给 {@link ScopedValueHolder}（首选）或 {@link ThreadLocalHolder}（降级），
 * 由 {@code TenantAutoConfiguration} 在启动时选定并注入。</p>
 *
 * <h2>使用方式</h2>
 * <pre>{@code
 * // 读取当前租户（只读，任何模式下均可调用）
 * Long tenantId = TenantContext.getTenantId();
 * TenantPrincipal principal = TenantContext.get();
 *
 * // 绑定租户上下文（Filter / MQ 消费者 / 定时任务入口调用）
 * TenantContext.runWithTenant(principal, () -> {
 *     // 业务逻辑
 * });
 * }</pre>
 *
 * <h2>作用域与生命周期</h2>
 * <p>租户上下文是<strong>线程局部</strong>的。下列操作会<strong>脱离</strong>当前作用域，
 * 导致子线程或异步任务中 {@link #get()} 返回 {@code null}：</p>
 * <ul>
 *   <li>{@link Thread#start()} — 新建非结构化线程</li>
 *   <li>{@code @Async} / {@link java.util.concurrent.ExecutorService#submit(java.util.concurrent.Callable)} —
 *       普通线程池线程不继承 ScopedValue</li>
 *   <li>{@link java.util.concurrent.CompletableFuture} 的异步链 / {@code Mono} / {@code Flux} 等
 *       Reactive 算子不传播 ScopedValue</li>
 * </ul>
 *
 * <p><strong>异步任务必须显式传递租户上下文</strong>，否则业务方法会因上下文丢失而
 * 抛出 {@link com.richie.component.tenant.exception.BusinessException}。
 * 推荐两种方式：</p>
 * <ol>
 *   <li>使用 {@link java.util.concurrent.StructuredTaskScope} 派生子任务 — ScopedValue 自动继承，
 *       且子任务生命周期受父任务约束</li>
 *   <li>使用 {@code TenantTaskDecorator}（{@code @Bean} 暴露），
 *       由框架自动织入 {@code ThreadPoolTaskExecutor} / {@code ThreadPoolTaskScheduler}
 *       （见 {@link com.richie.component.tenant.cross.TenantTaskDecoratorBeanPostProcessor}），
 *       提交任务前自动捕获当前租户并在执行时恢复</li>
 * </ol>
 *
 * @author richie696
 * @since 2.0
 */
public final class TenantContext {

    private static volatile TenantContextHolder holder;

    private TenantContext() {
    }

    // ==================== 初始化（由 AutoConfiguration 调用） ====================

    /**
     * 设置底层持有器实现（启动时调用一次）。
     *
     * @param tenantContextHolder ScopedValue 或 ThreadLocal 持有器
     */
    public static void init(TenantContextHolder tenantContextHolder) {
        holder = tenantContextHolder;
    }

    /**
     * 获取底层持有器（仅供框架内部使用）。
     *
     * @return 当前持有器实例
     */
    public static TenantContextHolder getHolder() {
        checkInitialized();
        return holder;
    }

    // ==================== 读取 API（所有模块统一调用） ====================

    /**
     * 获取当前线程的租户主体信息。
     *
     * @return 租户主体；未绑定时返回 {@code null}
     */
    public static TenantPrincipal get() {
        checkInitialized();
        return holder.get();
    }

    /**
     * 获取当前线程的租户 ID。
     *
     * @return 租户 ID；未绑定时返回 {@code null}
     */
    public static Long getTenantId() {
        TenantPrincipal principal = get();
        return principal != null ? principal.getTenantId() : null;
    }

    /**
     * 获取当前线程的租户展示名称。
     *
     * @return 租户名称；未绑定时返回 {@code null}
     */
    public static String getTenantName() {
        TenantPrincipal principal = get();
        return principal != null ? principal.getTenantName() : null;
    }

    /**
     * 获取当前线程的租户信息，不允许为空。
     *
     * @return 租户主体
     * @throws BusinessException 如果未绑定租户上下文
     */
    public static TenantPrincipal require() {
        TenantPrincipal principal = get();
        if (principal == null) {
            throw new BusinessException("Tenant context not bound, ensure request passed through TenantIdentityFilter");
        }
        return principal;
    }

    /**
     * 获取当前线程的租户 ID，不允许为空。
     *
     * @return 租户 ID
     * @throws BusinessException 如果未绑定租户上下文
     */
    public static Long requireTenantId() {
        return require().getTenantId();
    }

    // ==================== 绑定 API（入口层调用） ====================

    /**
     * 在指定租户上下文中执行任务（无返回值）。
     *
     * <p>核心入口方法。Filter、MQ 消费者、定时任务在此处绑定租户后执行业务逻辑。
     * 内置事务内租户切换检测：若当前事务已冻结某租户，且目标租户不同，则拒绝切换。</p>
     *
     * @param principal 租户主体信息
     * @param task      要在租户上下文中执行的任务
     * @throws TenantSwitchInTransactionException 事务内切换租户时
     */
    public static void runWithTenant(TenantPrincipal principal, Runnable task) {
        checkInitialized();
        checkTransactionTenantSwitch(principal);
        holder.runWithTenant(principal, task);
    }

    /**
     * 在指定租户上下文中执行任务（有返回值）。
     *
     * @param principal 租户主体信息
     * @param task      要在租户上下文中执行的任务
     * @param <T>       返回值类型
     * @return 任务执行结果
     * @throws TenantSwitchInTransactionException 事务内切换租户时
     */
    public static <T> T runWithTenant(TenantPrincipal principal, Supplier<T> task) {
        checkInitialized();
        checkTransactionTenantSwitch(principal);
        return holder.runWithTenant(principal, task);
    }

    /**
     * 清理当前线程的租户上下文。
     *
     * <p>ScopedValue 模式下为空实现；ThreadLocal 模式下移除 ThreadLocal 值。
     * 通常由 {@code runWithTenant} 自动管理，仅在手动 set/clear 场景下需要显式调用。</p>
     */
    public static void clear() {
        if (holder != null) {
            holder.clear();
        }
    }

    // ==================== 内部方法 ====================

    private static void checkInitialized() {
        if (holder == null) {
            throw new IllegalStateException(
                "TenantContext not initialized. Ensure TenantAutoConfiguration is loaded.");
        }
    }

    /**
     * 检测事务内租户切换（仅在 TransactionTenantHolder 可用时生效）。
     * 由持久层模块注入检测逻辑，此处通过反射或回调解耦。
     */
    private static void checkTransactionTenantSwitch(TenantPrincipal principal) {
        // 事务冻结检测由 TransactionTenantHolder 在持久层模块实现
        // 此处预留扩展点，通过 TransactionTenantChecker SPI 注入
        if (transactionChecker != null) {
            transactionChecker.check(principal.getTenantId());
        }
    }

    /**
     * 事务租户切换检测器 SPI（由持久层模块注册）。
     */
    @FunctionalInterface
    public interface TransactionTenantChecker {
        void check(Long targetTenantId);
    }

    /**
     *  注册事务租户切换检测器（由持久层自动配置调用）
     */
    @Setter
    private static volatile TransactionTenantChecker transactionChecker;

}
