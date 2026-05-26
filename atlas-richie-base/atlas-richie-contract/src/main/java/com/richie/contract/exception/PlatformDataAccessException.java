package com.richie.contract.exception;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.helpers.MessageFormatter;

/**
 * 平台数据访问异常
 *
 * @author richie696
 * @version 1.0
 * @since 2022-10-08 17:25:54
 */
@Slf4j
public class PlatformDataAccessException extends RuntimeException {

    /**
     * 默认构造函数
     */
    public PlatformDataAccessException() {
    }

    /**
     * 构造函数
     * @param message 异常信息
     * @param args 参数
     */
    public PlatformDataAccessException(String message, Object... args) {
        super(MessageFormatter.arrayFormat(message, args).getMessage());
    }

    /**
     * 构造函数
     * @param message 异常信息
     * @param cause 异常原因
     */
    public PlatformDataAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 构造函数
     * @param message 异常信息
     * @param args 参数
     * @param cause 异常原因
     */
    public PlatformDataAccessException(String message, Object[] args, Throwable cause) {
        super(MessageFormatter.arrayFormat(message, args).getMessage(), cause);
    }

    /**
     * 构造函数
     * @param cause 异常原因
     */
    public PlatformDataAccessException(Throwable cause) {
        super(cause);
    }

    /**
     * 构造函数
     * @param message 异常信息
     * @param cause 异常原因
     * @param enableSuppression 是否启用抑制异常
     * @param writableStackTrace 是否写入堆栈跟踪
     */
    public PlatformDataAccessException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    /**
     * 构造函数
     * @param message 异常信息
     * @param args 参数数组
     * @param cause 异常原因
     * @param enableSuppression 是否启用抑制异常
     * @param writableStackTrace 是否写入堆栈跟踪
     */
    public PlatformDataAccessException(String message, Object[] args, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(MessageFormatter.arrayFormat(message, args).getMessage(), cause, enableSuppression, writableStackTrace);
    }

}
