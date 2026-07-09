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
package com.richie.contract.exception;

import org.slf4j.helpers.MessageFormatter;

/**
 * 平台运行时异常
 *
 * @author richie696
 * @version 1.0
 * @since 2022-10-09 14:49:31
 */
public class PlatformRuntimeException extends RuntimeException {

    /**
     * 默认构造函数
     */
    public PlatformRuntimeException() {
    }

    /**
     * 构造函数
     * @param message 异常信息
     * @param args 参数
     */
    public PlatformRuntimeException(String message, Object... args) {
        super(MessageFormatter.arrayFormat(message, args).getMessage());
    }

    /**
     * 构造函数
     * @param message 异常信息
     * @param cause 异常原因
     */
    public PlatformRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 构造函数
     * @param message 异常信息
     * @param args 参数数组
     * @param cause 异常原因
     */
    public PlatformRuntimeException(String message, Object[] args, Throwable cause) {
        super(MessageFormatter.arrayFormat(message, args).getMessage(), cause);
    }

    /**
     * 构造函数
     * @param cause 异常原因
     */
    public PlatformRuntimeException(Throwable cause) {
        super(cause);
    }

    /**
     * 构造函数
     * @param message 异常信息
     * @param cause 异常原因
     * @param enableSuppression 是否启用抑制异常
     * @param writableStackTrace 是否写入堆栈跟踪
     */
    public PlatformRuntimeException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
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
    public PlatformRuntimeException(String message, Object[] args, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(MessageFormatter.arrayFormat(message, args).getMessage(), cause, enableSuppression, writableStackTrace);
    }

}
