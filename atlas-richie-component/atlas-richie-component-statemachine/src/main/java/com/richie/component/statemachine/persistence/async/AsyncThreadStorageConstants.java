package com.richie.component.statemachine.persistence.async;

/**
 * 异步线程池存储管理器常量
 * <p>
 * 仅限当前包访问，包含线程池、线程名称、键分隔符等常量定义。
 * 
 *
 * @author richie696
 * @since 5.0.0
 */
interface AsyncThreadStorageConstants {

    /**
     * 线程池关闭等待时间（秒）
     */
    int SHUTDOWN_WAIT_SECONDS = 5;

    /**
     * 历史记录键分隔符
     */
    String HISTORY_KEY_SEPARATOR = ":";
}
