/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.nats.strategy;

import io.nats.client.impl.Headers;

/**
 * NATS Header 提取策略接口（接收端）
 *
 * <p>从 NATS 消息 Headers 中提取上下文信息并恢复到 {@code HeaderContextHolder}。
 * 在 subscribe / consume 收到消息时自动调用。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
public interface NatsHeaderExtractor {

    /**
     * 从 NATS Headers 提取头信息并恢复到当前线程上下文
     *
     * @param headers NATS 消息 Headers
     */
    void extract(Headers headers);
}
