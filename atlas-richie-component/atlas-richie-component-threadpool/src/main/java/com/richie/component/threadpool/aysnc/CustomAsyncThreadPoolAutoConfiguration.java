package com.richie.component.threadpool.aysnc;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 自定义异步线程池
 *
 * @author yuy
 * @version 1.0
 * @since 2023-10-24 17:08:42
 */
@Slf4j
@EnableAsync
@RequiredArgsConstructor
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CustomAsyncThreadPoolProperties.class)
@ConditionalOnProperty(prefix = CustomAsyncConstant.PLATFORM_THREAD_POOL_PREFIX, value = "enable", havingValue = "true")
public class CustomAsyncThreadPoolAutoConfiguration {

    /** 自定义异步线程池配置属性 */
    private final CustomAsyncThreadPoolProperties customAsyncThreadPoolProperties;

    /**
     * 自定义异步线程池 Bean，供 @Async 使用（可选 MDC/数据源上下文传播）。
     *
     * @param mdcTaskDecorator 任务装饰器（用于上下文传播），可为 null
     * @return 已初始化的 ThreadPoolTaskExecutor
     */
    @Bean
    public ThreadPoolTaskExecutor customAsyncExecutor(MdcTaskDecorator mdcTaskDecorator) {
        //定义线程池
        var taskExecutor = new ThreadPoolTaskExecutor();
        //核心线程数
        taskExecutor.setCorePoolSize(customAsyncThreadPoolProperties.getCorePoolSize());
        //线程池最大线程数
        taskExecutor.setMaxPoolSize(customAsyncThreadPoolProperties.getMaxPoolSize());
        //线程队列最大线程数
        taskExecutor.setQueueCapacity(customAsyncThreadPoolProperties.getQueueCapacity());
        //线程名称前缀
        taskExecutor.setThreadNamePrefix(StringUtils.isNotEmpty(customAsyncThreadPoolProperties.getThreadNamePrefix()) ? customAsyncThreadPoolProperties.getThreadNamePrefix() : "Async-ThreadPool-");
        //线程池中线程最大空闲时间，默认：60，单位：秒
        taskExecutor.setKeepAliveSeconds(customAsyncThreadPoolProperties.getKeepAliveSeconds());
        //核心线程是否允许超时，默认:false
        taskExecutor.setAllowCoreThreadTimeOut(customAsyncThreadPoolProperties.isAllowCoreThreadTimeOut());
        //IOC容器关闭时是否阻塞等待剩余的任务执行完成，默认:false（必须设置setAwaitTerminationSeconds）
        taskExecutor.setWaitForTasksToCompleteOnShutdown(customAsyncThreadPoolProperties.isWaitForTasksToCompleteOnShutdown());
        //阻塞IOC容器关闭的时间，默认：10秒（必须设置 setWaitForTasksToCompleteOnShutdown）
        taskExecutor.setAwaitTerminationSeconds(customAsyncThreadPoolProperties.getAwaitTerminationSeconds());
        //拒绝策略 todo
        taskExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        if (customAsyncThreadPoolProperties.isEnableTaskDecorator()) {
            taskExecutor.setTaskDecorator(mdcTaskDecorator);
        }
        //初始化
        taskExecutor.initialize();
        return taskExecutor;
    }

    /**
     * MDC/数据源上下文传播的任务装饰器 Bean。
     *
     * @return MdcTaskDecorator 实例
     */
    @Bean
    public MdcTaskDecorator mdcTaskDecorator() {
        return new MdcTaskDecorator(customAsyncThreadPoolProperties);
    }

    /**
     * 覆盖 @Async 默认线程池的配置器（当 override-default-async=true 时生效）。
     *
     * @param customAsyncExecutor 自定义异步线程池
     * @return CustomAsyncConfigurer 实例
     */
    @Bean
    @ConditionalOnProperty(prefix = CustomAsyncConstant.PLATFORM_THREAD_POOL_PREFIX, value = "override-default-async", havingValue = "true")
    public CustomAsyncConfigurer customAsyncConfigurer(ThreadPoolTaskExecutor customAsyncExecutor) {
        return new CustomAsyncConfigurer(customAsyncExecutor);
    }

}
