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
package cn.richie696.component.nats.exception;

/**
 * NATS 连接异常
 *
 * @author richie696
 * @since 1.0.0
 */
public class NatsConnectionException extends NatsException {

    /**
     * 仅携带消息文本构造连接异常。
     *
     * @param message 异常描述
     */
    public NatsConnectionException(String message) {
        super(message);
    }

    /**
     * 携带消息文本与根因构造连接异常。
     *
     * @param message 异常描述
     * @param cause 触发本异常的根因
     */
    public NatsConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
