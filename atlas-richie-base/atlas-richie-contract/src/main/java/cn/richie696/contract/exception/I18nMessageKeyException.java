/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.richie696.contract.exception;

import java.util.Arrays;
import java.util.Objects;

/**
 * 携带国际化消息键的通用异常。
 *
 * <p>异常生产方只提供消息键和参数，具体的国际化解析及 HTTP 响应由边界层统一处理。
 * 该异常不依赖 Spring 或任何具体 Web 框架，便于组件在不同运行时中复用。</p>
 */
public class I18nMessageKeyException extends RuntimeException {

    private final String messageKey;
    private final Object[] arguments;
    private final int statusCode;
    private final String errorCode;

    /**
     * 创建一个使用 HTTP 状态码作为业务错误码的国际化异常。
     *
     * @param messageKey 国际化消息键
     * @param statusCode HTTP 状态码
     * @param arguments 国际化参数
     */
    public I18nMessageKeyException(String messageKey, int statusCode, Object... arguments) {
        this(messageKey, statusCode, String.valueOf(statusCode), arguments);
    }

    /**
     * 创建一个带独立业务错误码的国际化异常。
     *
     * @param messageKey 国际化消息键
     * @param statusCode HTTP 状态码
     * @param errorCode 业务错误码
     * @param arguments 国际化参数
     */
    public I18nMessageKeyException(String messageKey,
                                   int statusCode,
                                   String errorCode,
                                   Object... arguments) {
        super(Objects.requireNonNull(messageKey, "messageKey must not be null"));
        if (statusCode < 100 || statusCode > 599) {
            throw new IllegalArgumentException("statusCode must be between 100 and 599");
        }
        this.messageKey = messageKey;
        this.statusCode = statusCode;
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        this.arguments = arguments == null ? new Object[0] : arguments.clone();
    }

    public String getMessageKey() {
        return messageKey;
    }

    public Object[] getArguments() {
        return arguments.clone();
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String toString() {
        return "I18nMessageKeyException{" +
                "messageKey='" + messageKey + '\'' +
                ", arguments=" + Arrays.toString(arguments) +
                ", statusCode=" + statusCode +
                ", errorCode='" + errorCode + '\'' +
                '}';
    }
}
