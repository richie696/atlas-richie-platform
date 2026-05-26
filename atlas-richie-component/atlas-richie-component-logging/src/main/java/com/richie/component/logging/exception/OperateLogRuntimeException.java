package com.richie.component.logging.exception;

/**
 * 操作日志组件运行时异常。
 *
 * @author richie696
 * @version 1.0
 * @since 2022-10-09 15:58:05
 */
public class OperateLogRuntimeException extends RuntimeException {

    /** 默认构造函数 */
    public OperateLogRuntimeException() {
    }

    /**
     * 使用错误信息构造
     *
     * @param message 错误信息
     */
    public OperateLogRuntimeException(String message) {
        super(message);
    }

    /**
     * 使用错误信息与原因构造
     *
     * @param message 错误信息
     * @param cause   原因
     */
    public OperateLogRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 使用原因构造
     *
     * @param cause 原因
     */
    public OperateLogRuntimeException(Throwable cause) {
        super(cause);
    }

    /**
     * 完整构造（供 JDK 使用）
     *
     * @param message            错误信息
     * @param cause              原因
     * @param enableSuppression  是否启用抑制
     * @param writableStackTrace 是否写入堆栈
     */
    protected OperateLogRuntimeException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
