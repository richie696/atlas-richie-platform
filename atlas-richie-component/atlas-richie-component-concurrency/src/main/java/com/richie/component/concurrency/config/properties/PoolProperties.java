package com.richie.component.concurrency.config.properties;


import lombok.Data;

import java.time.Duration;

/**
 * 单个命名线程池的参数配置
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
public class PoolProperties {

    /** 核心线程数。即使没有任务执行也会保留这么多线程。 */
    private int corePoolSize = 4;

    /** 最大线程数。当任务队列满时会创建新线程直到达到此上限。 */
    private int maximumPoolSize = 8;

    /** 空闲线程存活时间。超过核心线程数的线程空闲此时间后将被回收。 */
    private Duration keepAliveTime = Duration.ofSeconds(60);

    /** 任务队列容量。基于此值创建 {@link java.util.concurrent.LinkedBlockingQueue}。 */
    private int queueCapacity = 2000;

    /** 线程名前缀。为空时默认使用 {@code poolName-} 作为前缀。 */
    private String threadNamePrefix = "";

    /**
     * 拒绝策略类型（大小写不敏感）。
     * {@code AbortPolicy}（默认）、{@code CallerRunsPolicy}、{@code DiscardPolicy}、{@code DiscardOldestPolicy}。
     */
    private String rejectedHandler = "AbortPolicy";
}
