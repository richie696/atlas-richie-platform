package com.richie.component.mfa.core.event;

import com.richie.component.mfa.core.constant.MfaOperationTypeEnum;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * MFA 审计事件
 * <p>
 * MFA 组件通过 Spring ApplicationEventPublisher 发布此事件，业务系统通过 @EventListener 监听并自行决定如何处理
 * （持久化、签名、归档等）。
 * <p>
 * MFA 组件只负责发布事件，不直接写数据库，保持组件轻量化和独立性。
 *
 * @author richie696
 * @since 5.0.0
 */
@Getter
public class MfaAuditEvent extends ApplicationEvent {

    /**
     * 租户ID（可选，如果未启用租户则为 null）
     */
    private final String tenantId;

    /**
     * 用户ID（必填，业务系统User表的主键ID）
     */
    private final String userId;

    /**
     * 操作类型（BIND/UNBIND/VERIFY/ACTIVATE/DISABLE）
     */
    private final MfaOperationTypeEnum operationType;

    /**
     * 认证方式（TOTP/HOTP/SMS/EMAIL/BACKUP_CODE）
     */
    private final String authMethod;

    /**
     * IP地址（可选）
     */
    private final String ipAddress;

    /**
     * 用户代理（可选）
     */
    private final String userAgent;

    /**
     * 设备ID（可选）
     */
    private final String deviceId;

    /**
     * 操作结果（SUCCESS/FAILED/BLOCKED）
     */
    private final String result;

    /**
     * 错误码（失败时使用）
     */
    private final String errorCode;

    /**
     * 错误消息（失败时使用）
     */
    private final String errorMessage;

    /**
     * 操作耗时（毫秒，可选）
     */
    private final Long durationMs;

    /**
     * 事件时间戳
     */
    private final OffsetDateTime eventTimestamp;

    /**
     * 构造函数
     *
     * @param source        事件源（通常为发布事件的组件实例）
     * @param tenantId      租户ID
     * @param userId        用户ID
     * @param operationType 操作类型
     * @param authMethod    认证方式
     * @param ipAddress     IP地址
     * @param userAgent     用户代理
     * @param deviceId      设备ID
     * @param result        操作结果
     * @param errorCode     错误码
     * @param errorMessage  错误消息
     * @param durationMs    操作耗时
     * @param eventTimestamp     事件时间戳
     */
    public MfaAuditEvent(Object source,
                        String tenantId,
                        String userId,
                        MfaOperationTypeEnum operationType,
                        String authMethod,
                        String ipAddress,
                        String userAgent,
                        String deviceId,
                        String result,
                        String errorCode,
                        String errorMessage,
                        Long durationMs,
                        OffsetDateTime eventTimestamp) {
        this.eventTimestamp = eventTimestamp != null ? eventTimestamp : OffsetDateTime.now(ZoneOffset.UTC);
        this.tenantId = tenantId;
        this.userId = userId;
        this.operationType = operationType;
        this.authMethod = authMethod;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.deviceId = deviceId;
        this.result = result;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.durationMs = durationMs;
        super(source);
    }

    /**
     * 创建审计事件的 Builder
     *
     * @param source 事件源（通常为发布事件的组件实例，如 this）
     * @return Builder 实例
     */
    public static MfaAuditEventBuilder builder(Object source) {
        return new MfaAuditEventBuilder(source);
    }

    /**
     * MFA 审计事件 Builder
     */
    public static class MfaAuditEventBuilder {
        private final Object source;
        private String tenantId;
        private String userId;
        private MfaOperationTypeEnum operationType;
        private String authMethod;
        private String ipAddress;
        private String userAgent;
        private String deviceId;
        private String result;
        private String errorCode;
        private String errorMessage;
        private Long durationMs;
        private OffsetDateTime timestamp;

        public MfaAuditEventBuilder(Object source) {
            this.source = source;
        }

        public MfaAuditEventBuilder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public MfaAuditEventBuilder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public MfaAuditEventBuilder operationType(MfaOperationTypeEnum operationType) {
            this.operationType = operationType;
            return this;
        }

        public MfaAuditEventBuilder authMethod(String authMethod) {
            this.authMethod = authMethod;
            return this;
        }

        public MfaAuditEventBuilder ipAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public MfaAuditEventBuilder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public MfaAuditEventBuilder deviceId(String deviceId) {
            this.deviceId = deviceId;
            return this;
        }

        public MfaAuditEventBuilder result(String result) {
            this.result = result;
            return this;
        }

        public MfaAuditEventBuilder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public MfaAuditEventBuilder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public MfaAuditEventBuilder durationMs(Long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public MfaAuditEventBuilder timestamp(OffsetDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public MfaAuditEvent build() {
            return new MfaAuditEvent(
                source,
                tenantId,
                userId,
                operationType,
                authMethod,
                ipAddress,
                userAgent,
                deviceId,
                result,
                errorCode,
                errorMessage,
                durationMs,
                timestamp
            );
        }
    }
}
