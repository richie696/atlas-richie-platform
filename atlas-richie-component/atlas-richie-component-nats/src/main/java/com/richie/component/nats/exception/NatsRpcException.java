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
package com.richie.component.nats.exception;

/**
 * NATS RPC 请求-响应异常
 *
 * <p>区分 Timeout（超时）和 NoResponders（无响应者）两种语义。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
public class NatsRpcException extends NatsException {

    private final boolean timeout;
    private final boolean noResponders;

    public NatsRpcException(String message, Throwable cause, boolean timeout, boolean noResponders) {
        super(message, cause);
        this.timeout = timeout;
        this.noResponders = noResponders;
    }

    public static NatsRpcException timeout(String subject, Throwable cause) {
        return new NatsRpcException("RPC request timed out for subject: " + subject, cause, true, false);
    }

    public static NatsRpcException noResponders(String subject, Throwable cause) {
        return new NatsRpcException("No responders available for subject: " + subject, cause, false, true);
    }

    public static NatsRpcException other(String subject, Throwable cause) {
        return new NatsRpcException("RPC request failed for subject: " + subject, cause, false, false);
    }

    public boolean isTimeout() {
        return timeout;
    }

    public boolean isNoResponders() {
        return noResponders;
    }
}
