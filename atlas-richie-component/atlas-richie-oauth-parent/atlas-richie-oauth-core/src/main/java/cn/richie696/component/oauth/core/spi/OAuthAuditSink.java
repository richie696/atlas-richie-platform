package cn.richie696.component.oauth.core.spi;

import java.time.Instant;
import java.util.Map;

/**
 * OAuth 审计事件的统一出口。
 * <p>
 * 由 {@link cn.richie696.component.oauth.core.TokenEndpoint} 在 Token 签发/刷新/重放/token_introspection
 * 等关键节点调用;事件模型刻意不包含完整 token、client secret 或密码,只携带 clientId/subject/
 * tenantId/resource/ip/success/errorCode/attributes,避免审计日志成为新的敏感数据泄露源。
 * </p>
 * <p>
 * 处于 oauth-core 的可观测性位置:默认走日志实现(见
 * {@link cn.richie696.component.oauth.core.support.LoggingOAuthAuditSink}),OAuth Service 应注入
 * DB / 消息队列 / 文件审计实现,把审计数据落到 SIEM。
 * </p>
 * <p>
 * 解决的问题:在 Token 端点这种高频路径上提供轻量、可关闭的审计出口;同时通过事件模型强制脱敏,
 * 让"审计里出现明文 secret"成为不可能事件。
 * </p>
 *
 * @author richie696
 * @since 2026-08-07
 */
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
