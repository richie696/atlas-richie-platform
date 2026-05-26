package com.richie.component.threadpool.aysnc;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;

/**
 * 自定义异步线程池配置常量。
 *
 * @author yuy
 * @version 1.0
 * @since 2023-10-24 17:12:57
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CustomAsyncConstant {

    /** 自定义异步线程池配置前缀（platform.thread-pool） */
    public static final String PLATFORM_THREAD_POOL_PREFIX = "platform.thread-pool";

}
