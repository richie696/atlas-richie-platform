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

import cn.richie696.context.common.api.HeaderContextHolder;
import io.nats.client.impl.Headers;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * 默认 NATS Header 提取实现（接收端）
 *
 * <p>从 NATS 消息 Headers 中提取白名单内的头信息，恢复到 {@link HeaderContextHolder} 中。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class DefaultNatsHeaderExtractor implements NatsHeaderExtractor {

    /**
     * 允许跨链路传播的 Header 名称白名单。
     * 通过 {@code Set.copyOf} 拷贝为不可变集合，与发送端注入器保持一致的语义。
     */
    private final Set<String> propagatedHeaders;

    /**
     * @param headers 需要跨链路传播的 Header 名称白名单；构造期完成不可变拷贝
     */
    public DefaultNatsHeaderExtractor(Set<String> headers) {
        this.propagatedHeaders = Set.copyOf(headers);
    }

    /**
     * 将 NATS Headers 中匹配白名单的字段恢复到当前线程 {@link HeaderContextHolder}。
     *
     * @param headers 收到的 NATS Headers，可为 {@code null} 或为空（无任何头信息）
     */
    @Override
    public void extract(Headers headers) {
        // Headers 为 null/空时直接短路，避免不必要的遍历；这是 NATS Core 订阅端常见场景。
        if (headers == null || headers.isEmpty()) {
            return;
        }
        for (String key : propagatedHeaders) {
            var values = headers.get(key);
            // Header 在 NATS 中天然支持多值，这里只取首个值回填上下文，匹配注入端的单值语义。
            if (values != null && !values.isEmpty()) {
                var value = values.getFirst();
                if (value != null) {
                    HeaderContextHolder.setHeader(key, value);
                    log.trace("NATS header extractor: restored [{}]", key);
                }
            }
        }
    }
}
