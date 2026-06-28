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
