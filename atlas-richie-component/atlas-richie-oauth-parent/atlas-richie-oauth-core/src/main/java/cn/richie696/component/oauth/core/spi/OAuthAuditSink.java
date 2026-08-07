package cn.richie696.component.oauth.core.spi;

import java.time.Instant;
import java.util.Map;

/** OAuth 审计出口。事件模型刻意不包含完整 token、client secret 或密码。 */
@FunctionalInterface
public interface OAuthAuditSink {

    void record(OAuthAuditEvent event);

    static OAuthAuditSink noOp() {
        return event -> { };
    }

    record OAuthAuditEvent(String eventType, String clientId, String subject,
                           String tenantId, String resource, String ip,
                           boolean success, String errorCode, Instant occurredAt,
                           Map<String, String> attributes) {
        public OAuthAuditEvent {
            occurredAt = occurredAt == null ? Instant.now() : occurredAt;
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }
    }
}
