package com.richie.component.nats.strategy;

import com.richie.component.nats.strategy.NatsHeaderExtractor;
import com.richie.context.common.api.HeaderContextHolder;
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

    private final Set<String> propagatedHeaders;

    public DefaultNatsHeaderExtractor(Set<String> headers) {
        this.propagatedHeaders = Set.copyOf(headers);
    }

    @Override
    public void extract(Headers headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        for (String key : propagatedHeaders) {
            var values = headers.get(key);
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
