package com.richie.component.threadpool.aysnc;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 异步线程池配置
 *
 * @author yuy
 * @version 1.0
 * @since 2023-10-24 17:08:42
 */
@Data
@ConfigurationProperties(prefix = CustomAsyncConstant.PLATFORM_THREAD_POOL_PREFIX)
public class CustomAsyncThreadPoolProperties {

    /**
     * 是否启动异步线程池，默认 false
     */
    private boolean enable;

    /**
     * 核心线程数,默认：Java虚拟机可用线程数
     */
    private int corePoolSize = Runtime.getRuntime().availableProcessors() * 2;

    /**
     * 线程池最大线程数
     */
    private int maxPoolSize = Runtime.getRuntime().availableProcessors() * 4;

    /**
     * 线程队列最大线程数
     */
    private int queueCapacity = 2000;

    /**
     * 自定义线程名前缀，默认：customtl-
     */
    private String threadNamePrefix = "customtl-";

    /**
     * 线程池中线程最大空闲时间，默认：60，单位：秒
     */
    private int keepAliveSeconds = 60;

    /**
     * 核心线程是否允许超时，默认false
     */
    private boolean allowCoreThreadTimeOut;

    /**
     * IOC容器关闭时是否阻塞等待剩余的任务执行完成（必须设置 setAwaitTerminationSeconds），默认false
     */
    private boolean waitForTasksToCompleteOnShutdown;

    /**
     * 阻塞IOC容器关闭的时间（必须设置 waitForTasksToCompleteOnShutdown）
     */
    private int awaitTerminationSeconds = 120;

    /**
     * 是否覆盖@Async注解的默认线程池，默认false
     */
    private boolean overrideDefaultAsync;

    /**
     * 是否启用线程上下文传播参数，默认false
     */
    private boolean enableTaskDecorator;

    /**
     * 是否启用DynamicDataSourceContextHolder传播参数，默认false
     */
    private boolean enableDynamicDataSourceContextHolder;

}
