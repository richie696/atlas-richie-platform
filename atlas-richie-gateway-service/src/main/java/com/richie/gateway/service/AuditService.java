package com.richie.gateway.service;

import com.richie.contract.gateway.model.OAuth2AuditEvent;

/**
 * 网关审计服务接口
 * <p>
 * 职责：
 * - 提供统一的审计事件发布能力（网关只负责发消息，不直接写库）
 * - 对外暴露认证事件 / 访问事件 / 异常行为的审计入口
 * <p>
 * 说明：
 * - 具体实现类通过 Redis Stream 将事件发送到审计服务（如 general-service），由审计服务异步持久化到数据库
 * - 虽然事件模型名为 OAuth2AuditEvent，但本服务不仅处理 OAuth2.0 事件，还处理通用异常检测和访问事件
 *
 * @author richie696
 * @version 1.0
 * @since 2025-12-18
 */
public interface AuditService {

    // ==================== 底层通用接口（以 Event 为中心） ====================

    /**
     * 记录认证事件（Token 颁发 / 刷新 / 撤销）
     * <p>建议通过 {@link OAuth2AuditEvent#getEventType()} 标识具体事件类型（TOKEN_ISSUED / TOKEN_REFRESHED 等）。
     */
    void auditAuthEvent(OAuth2AuditEvent event);

    /**
     * 记录访问事件（资源访问成功 / 失败）
     * <p>建议通过 {@link OAuth2AuditEvent#getEventType()} 标识具体事件类型（ACCESS_GRANTED / ACCESS_DENIED）。
     */
    void auditAccessEvent(OAuth2AuditEvent event);

    /**
     * 记录异常行为（如可疑攻击、频繁失败等）
     * <p>建议通过 {@link OAuth2AuditEvent#getEventType()} 使用 SUSPICIOUS_ACTIVITY，并在 activityType / details 中描述具体异常。
     */
    void auditSuspiciousActivity(OAuth2AuditEvent event);

    // ==================== 语义化封装（仅传必要参数） ====================

    /**
     * access_token 颁发成功（TOKEN_ISSUED）
     */
    void auditTokenIssued(String clientId,
                          String clientName,
                          String ip,
                          String userAgent,
                          String tokenId,
                          long expiresInSeconds);

    /**
     * access_token 颁发失败（TOKEN_ISSUE_FAILED）
     */
    void auditTokenIssueFailed(String clientId,
                               String ip,
                               String userAgent,
                               String errorCode,
                               String errorMsg);

    /**
     * refresh_token 刷新成功（TOKEN_REFRESHED）
     */
    void auditTokenRefreshed(String clientId,
                             String ip,
                             String userAgent,
                             String tokenId,
                             long expiresInSeconds);

    /**
     * refresh_token 刷新失败（TOKEN_REFRESHED_FAILED）
     */
    void auditTokenRefreshFailed(String clientId,
                                 String ip,
                                 String userAgent,
                                 String errorCode,
                                 String errorMsg);

    /**
     * token 撤销（TOKEN_REVOKED）
     */
    void auditTokenRevoked(String clientId,
                           String tokenId,
                           String ip,
                           String userAgent,
                           String reason);

    /**
     * 访问通过（ACCESS_GRANTED）
     */
    void auditAccessGranted(String clientId,
                            String path,
                            String method,
                            String ip,
                            String userAgent);

    /**
     * 访问被拒绝（ACCESS_DENIED）
     */
    void auditAccessDenied(String clientId,
                           String path,
                           String method,
                           String ip,
                           String userAgent,
                           String reason,
                           String errorCode,
                           String errorMsg);

    /**
     * 异常行为（SUSPICIOUS_ACTIVITY）
     */
    void auditSuspiciousActivity(String clientId,
                                 String ip,
                                 String activityType,
                                 String details);
}
