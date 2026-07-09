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

import com.richie.component.nats.strategy.NatsHeaderInjector;
import com.richie.context.common.api.HeaderContextHolder;
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

    private final Set<String> propagatedHeaders;

    public DefaultNatsHeaderInjector(Set<String> headers) {
        this.propagatedHeaders = Set.copyOf(headers);
    }

    @Override
    public void inject(Headers headers) {
        for (String key : propagatedHeaders) {
            var value = HeaderContextHolder.getHeader(key);
            if (StringUtils.isNotBlank(value)) {
                headers.put(key, value);
                log.trace("NATS header injector: injected [{}]", key);
            }
        }
    }
}
