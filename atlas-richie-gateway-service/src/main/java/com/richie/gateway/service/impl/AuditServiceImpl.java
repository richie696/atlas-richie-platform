package com.richie.gateway.service.impl;

import com.richie.gateway.config.GatewayConfig;
import com.richie.contract.gateway.model.OAuth2AuditEvent;
import com.richie.contract.gateway.model.OAuth2AuditEventType;
import com.richie.component.redis.streammq.StreamMQ;
import com.richie.gateway.constants.GatewayRedisKey;
import com.richie.gateway.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * 网关审计服务实现
 *
 * @author richie696
 * @version 1.0
 * @since 2025-12-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final GatewayConfig gatewayConfig;


    @Override
    public void auditAuthEvent(OAuth2AuditEvent event) {
        publishAuditEvent(event);
    }

    @Override
    public void auditAccessEvent(OAuth2AuditEvent event) {
        publishAuditEvent(event);
    }

    @Override
    public void auditSuspiciousActivity(OAuth2AuditEvent event) {
        publishAuditEvent(event);
    }

    // ===== 语义化封装：按事件类型划分的便捷方法（仅接收必要参数） =====

    @Override
    public void auditTokenIssued(String clientId,
                                 String clientName,
                                 String ip,
                                 String userAgent,
                                 String tokenId,
                                 long expiresInSeconds) {
        OAuth2AuditEvent event = OAuth2AuditEvent.builder()
                .eventType(OAuth2AuditEventType.TOKEN_ISSUED)
                .clientId(clientId)
                .clientName(clientName)
                .ip(ip)
                .userAgent(userAgent)
                .tokenId(tokenId)
                .result("SUCCESS")
                .build();
        auditAuthEvent(event);
    }

    @Override
    public void auditTokenIssueFailed(String clientId,
                                      String ip,
                                      String userAgent,
                                      String errorCode,
                                      String errorMsg) {
        OAuth2AuditEvent event = OAuth2AuditEvent.builder()
                .eventType(OAuth2AuditEventType.TOKEN_ISSUE_FAILED)
                .clientId(clientId)
                .ip(ip)
                .userAgent(userAgent)
                .result("FAILED")
                .errorCode(errorCode)
                .errorMsg(errorMsg)
                .build();
        auditAuthEvent(event);
    }

    @Override
    public void auditTokenRefreshed(String clientId,
                                    String ip,
                                    String userAgent,
                                    String tokenId,
                                    long expiresInSeconds) {
        OAuth2AuditEvent event = OAuth2AuditEvent.builder()
                .eventType(OAuth2AuditEventType.TOKEN_REFRESHED)
                .clientId(clientId)
                .ip(ip)
                .userAgent(userAgent)
                .tokenId(tokenId)
                .result("SUCCESS")
                .build();
        auditAuthEvent(event);
    }

    @Override
    public void auditTokenRefreshFailed(String clientId,
                                        String ip,
                                        String userAgent,
                                        String errorCode,
                                        String errorMsg) {
        OAuth2AuditEvent event = OAuth2AuditEvent.builder()
                .eventType(OAuth2AuditEventType.TOKEN_REFRESHED_FAILED)
                .clientId(clientId)
                .ip(ip)
                .userAgent(userAgent)
                .result("FAILED")
                .errorCode(errorCode)
                .errorMsg(errorMsg)
                .build();
        auditAuthEvent(event);
    }

    @Override
    public void auditTokenRevoked(String clientId,
                                  String tokenId,
                                  String ip,
                                  String userAgent,
                                  String reason) {
        OAuth2AuditEvent event = OAuth2AuditEvent.builder()
                .eventType(OAuth2AuditEventType.TOKEN_REVOKED)
                .clientId(clientId)
                .ip(ip)
                .userAgent(userAgent)
                .tokenId(tokenId)
                .reason(reason)
                .result("SUCCESS")
                .build();
        auditAuthEvent(event);
    }

    @Override
    public void auditAccessGranted(String clientId,
                                   String path,
                                   String method,
                                   String ip,
                                   String userAgent) {
        OAuth2AuditEvent event = OAuth2AuditEvent.builder()
                .eventType(OAuth2AuditEventType.ACCESS_GRANTED)
                .clientId(clientId)
                .path(path)
                .method(method)
                .ip(ip)
                .userAgent(userAgent)
                .result("SUCCESS")
                .build();
        auditAccessEvent(event);
    }

    @Override
    public void auditAccessDenied(String clientId,
                                  String path,
                                  String method,
                                  String ip,
                                  String userAgent,
                                  String reason,
                                  String errorCode,
                                  String errorMsg) {
        OAuth2AuditEvent event = OAuth2AuditEvent.builder()
                .eventType(OAuth2AuditEventType.ACCESS_DENIED)
                .clientId(clientId)
                .path(path)
                .method(method)
                .ip(ip)
                .userAgent(userAgent)
                .reason(reason)
                .result("FAILED")
                .errorCode(errorCode)
                .errorMsg(errorMsg)
                .build();
        auditAccessEvent(event);
    }

    @Override
    public void auditSuspiciousActivity(String clientId,
                                        String ip,
                                        String activityType,
                                        String details) {
        OAuth2AuditEvent event = OAuth2AuditEvent.builder()
                .eventType(OAuth2AuditEventType.SUSPICIOUS_ACTIVITY)
                .clientId(clientId)
                .ip(ip)
                .activityType(activityType)
                .details(details)
                .result("FAILED")
                .build();
        auditSuspiciousActivity(event);
    }

    /**
     * 发布审计事件到 Redis Stream
     * <p>
     * 注意：此方法为异步非阻塞，失败时仅记录日志，不影响主流程
     */
    private void publishAuditEvent(OAuth2AuditEvent event) {
        // 检查审计日志总开关
        if (!gatewayConfig.isAuditEnabled()) {
            if (log.isTraceEnabled()) {
                log.trace("审计日志功能已关闭，跳过事件发布: eventType={}, clientId={}",
                        event.getEventType(), event.getClientId());
            }
            return;
        }

        try {
            // 补充默认字段
            if (event.getTimestamp() == null) {
                event.setTimestamp(OffsetDateTime.now());
            }
            if (event.getRequestId() == null) {
                event.setRequestId(MDC.get("traceId"));
            }

            // 发布到 Redis Stream
                StreamMQ.stream().publish(GatewayRedisKey.OAUTH2_AUDIT_EVENTS.getKey(), event);

            if (log.isDebugEnabled()) {
                log.debug("审计事件已发布: eventType={}, clientId={}, ip={}",
                        event.getEventType(), event.getClientId(), event.getIp());
            }
        } catch (Exception e) {
            // 审计日志失败不应影响主流程，仅记录错误日志
            log.error("发布审计事件失败: eventType={}, clientId={}",
                    event.getEventType(), event.getClientId(), e);
        }
    }
}
