package cn.richie696.component.oauth.core.support;

import cn.richie696.component.oauth.core.spi.OAuthAuditSink;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于 SLF4J 的默认 {@link OAuthAuditSink} 实现。
 * <p>
 * 把审计事件以脱敏形式写入日志(只携带 clientId/subject/tenantId/resource/ip/success/errorCode,
 * 不写 token 或 secret);OAuth Service 应注入 DB / 消息 / 文件审计实现,把审计数据落到 SIEM。
 * </p>
 * <p>
 * 处于 oauth-core 的默认审计实现位置:由 {@link cn.richie696.component.oauth.core.config.OAuth2AutoConfiguration}
 * 作为 {@link OAuthAuditSink} 的默认 Bean 注册;Token 端点的所有关键事件(签发、刷新、重放、撤销)
 * 都会经由本类落地。
 * </p>
 * <p>
 * 解决的问题:在没有审计后端的开发/演示环境,提供一个零依赖、可观察的默认出口,避免"忘配审计"
 * 导致关键事件丢失;同时通过只记录脱敏字段,让审计日志本身不成为新的敏感数据源。
 * </p>
 *
 * @author richie696
 * @since 2026-08-07
 */
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
