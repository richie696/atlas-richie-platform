package com.richie.contract.gateway.model;

import com.richie.contract.model.BaseStreamMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * OAuth2.0 审计事件消息体
 * <p>
 * 用于通过 Redis Stream 异步传输审计日志，网关侧发布，消费者侧持久化到数据库
 *
 * @author richie696
 * @version 1.0
 * @since 2025-12-17
 */
@Data
@Builder
@AllArgsConstructor
public class OAuth2AuditEvent implements BaseStreamMessage {


    /**
     * 事件类型
     */
    private OAuth2AuditEventType eventType;

    /**
     * 事件时间戳
     */
    private OffsetDateTime timestamp;

    /**
     * 客户端ID
     */
    private String clientId;

    /**
     * 客户端名称
     */
    private String clientName;

    /**
     * 客户端IP地址
     */
    private String ip;

    /**
     * 用户代理
     */
    private String userAgent;

    /**
     * 请求路径
     */
    private String path;

    /**
     * HTTP方法
     */
    private String method;

    /**
     * Token ID（JWT的jti字段）
     */
    private String tokenId;

    /**
     * Token类型（access_token/refresh_token）
     */
    private String tokenType;

    /**
     * 授权类型（client_credentials/refresh_token）
     */
    private String grantType;

    /**
     * 结果（SUCCESS/FAILED）
     */
    private String result;

    /**
     * 错误码
     */
    private String errorCode;

    /**
     * 错误消息
     */
    private String errorMsg;

    /**
     * 拒绝原因（token_invalid/token_expired/ip_not_allowed等）
     */
    private String reason;

    /**
     * 元数据（扩展信息，JSON格式）
     */
    private Map<String, Object> metadata;

    /**
     * 请求追踪ID（traceId）
     */
    private String requestId;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 异常行为类型（仅当 eventType=SUSPICIOUS_ACTIVITY 时使用）
     */
    private String activityType;

    /**
     * 异常行为详情（仅当 eventType=SUSPICIOUS_ACTIVITY 时使用）
     */
    private String details;

    /** 默认构造函数，供反序列化及 Builder 使用。 */
    public OAuth2AuditEvent() {
    }
}
