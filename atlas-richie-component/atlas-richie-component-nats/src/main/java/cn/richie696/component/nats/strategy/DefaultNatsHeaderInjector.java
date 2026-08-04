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
import org.apache.commons.lang3.StringUtils;

import java.util.Set;

/**
 * 默认 NATS Header 注入实现（发送端）
 *
 * <p>从 {@link HeaderContextHolder} 中读取白名单内的头信息，注入到 NATS 消息 Headers 中。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class DefaultNatsHeaderInjector implements NatsHeaderInjector {

    /**
     * 允许跨链路传播的 Header 名称白名单。
     * 通过 {@code Set.copyOf} 拷贝为不可变集合，避免运行期被调用方修改影响后续注入行为。
     */
    private final Set<String> propagatedHeaders;

    /**
     * @param headers 需要跨链路传播的 Header 名称白名单；构造期完成不可变拷贝
     */
    public DefaultNatsHeaderInjector(Set<String> headers) {
        this.propagatedHeaders = Set.copyOf(headers);
    }

    /**
     * 将当前线程 {@link HeaderContextHolder} 中匹配白名单的头信息写入 NATS 消息 Headers。
     *
     * @param headers 待写入的目标 NATS Headers 对象，由调用方提供
     */
    @Override
    public void inject(Headers headers) {
        // 仅同步白名单字段，避免把租户密钥等敏感上下文一并推到 broker。
        for (String key : propagatedHeaders) {
            var value = HeaderContextHolder.getHeader(key);
            if (StringUtils.isNotBlank(value)) {
                headers.put(key, value);
                log.trace("NATS header injector: injected [{}]", key);
            }
        }
    }
}
