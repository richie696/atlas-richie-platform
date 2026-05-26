package com.richie.component.threadpool.aysnc;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 对@Async注解覆盖
 *
 * @author yuy
 * @version 1.0
 * @since 2023-10-20 10:24:18
 */
@Slf4j
@RequiredArgsConstructor
public class CustomAsyncConfigurer implements AsyncConfigurer {

    /** 自定义异步线程池 */
    private final ThreadPoolTaskExecutor customAsyncExecutor;

    /**
     * 返回用于 @Async 的线程池。
     *
     * @return 自定义线程池
     */
    @Override
    public Executor getAsyncExecutor() {
        return customAsyncExecutor;
    }

    /**
     * 异步方法未捕获异常时的处理器（记录日志）。
     *
     * @return 异常处理器
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> {
            var msg ="";
            if (ArrayUtils.isNotEmpty(params)) {
                for (Object object : params) {
                    msg = StringUtils.join(msg, object, ";");
                }
            }
            msg = StringUtils.join(msg, throwable.getMessage());
            log.error("getAsyncUncaughtExceptionHandler error method:{} params:{}", method, msg, throwable);
        };
    }

}
