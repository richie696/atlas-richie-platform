/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
