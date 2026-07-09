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
package com.richie.component.nats.pipeline;

import io.nats.client.Message;

/**
 * NATS 消息处理函数式接口
 *
 * <p>所有订阅端消息处理的统一抽象，业务代码实现此接口。
 * 装饰器链通过 {@link NatsMessageHandlerPipeline} 在外层包装横切关注点。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@FunctionalInterface
public interface NatsMessageHandler {

    /**
     * 处理 NATS 消息
     *
     * @param message NATS 原始消息
     * @throws Exception 处理异常
     */
    void handle(Message message) throws Exception;
}
