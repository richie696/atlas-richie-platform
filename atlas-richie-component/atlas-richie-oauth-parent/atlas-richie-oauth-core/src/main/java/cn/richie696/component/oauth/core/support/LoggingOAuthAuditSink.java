package cn.richie696.component.oauth.core.support;

import cn.richie696.component.oauth.core.spi.OAuthAuditSink;
import lombok.extern.slf4j.Slf4j;

/** 默认审计实现只记录脱敏事件；OAuth Service 应注入 DB/消息/文件审计实现。 */
@Slf4j
public final class LoggingOAuthAuditSink implements OAuthAuditSink {

    @Override
    public void record(OAuthAuditEvent event) {
        log.info("OAuth audit: type={}, clientId={}, subject={}, tenantId={}, resource={}, "
                        + "ip={}, success={}, errorCode={}, attributes={}",
                event.eventType(), event.clientId(), event.subject(), event.tenantId(),
                event.resource(), event.ip(), event.success(), event.errorCode(), event.attributes());
    }
}
