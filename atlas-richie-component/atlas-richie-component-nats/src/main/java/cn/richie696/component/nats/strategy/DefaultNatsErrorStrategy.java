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
package cn.richie696.component.nats.strategy;

import io.nats.client.Message;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认 NATS 错误处理策略实现
 *
 * <p>记录发布和消费错误。JetStream 重试由服务器的 consumer 配置管理。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class DefaultNatsErrorStrategy implements NatsErrorStrategy {

    /**
     * 记录发布失败的 subject 与载荷长度（不输出实际内容，避免日志敏感信息泄漏）。
     *
     * @param subject NATS subject
     * @param data    待发送的载荷字节数组（可空）
     * @param e       导致发布失败的异常
     */
    @Override
    public void onPublishError(String subject, byte[] data, Exception e) {
        log.error("NATS publish error on subject [{}], data length={}", subject,
                data != null ? data.length : 0, e);
    }

    /**
     * 记录消费失败的 subject 与异常堆栈。JetStream 重试由 broker 的 consumer 配置承担，这里只观测，不发起本地重试。
     *
     * @param subject NATS subject
     * @param msg     触发失败的原始 NATS 消息
     * @param e       业务处理抛出的异常
     */
    @Override
    public void onConsumeError(String subject, Message msg, Exception e) {
        log.error("NATS consume error on subject [{}]", subject, e);
    }

}
