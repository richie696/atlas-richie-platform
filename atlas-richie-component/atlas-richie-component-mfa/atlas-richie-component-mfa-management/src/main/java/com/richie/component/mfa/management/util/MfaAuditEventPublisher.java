/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.mfa.management.util;

import com.richie.component.mfa.core.constant.MfaOperationTypeEnum;
import com.richie.component.mfa.core.event.MfaAuditEvent;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * MFA 审计事件发布工具类
 * <p>
 * 封装审计事件的发布逻辑，自动提取 IP 和 User-Agent 等信息。
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class MfaAuditEventPublisher {

    private final ApplicationEventPublisher eventPublisher;
    private final Object source;

    public MfaAuditEventPublisher(ApplicationEventPublisher eventPublisher, Object source) {
        this.eventPublisher = eventPublisher;
        this.source = source;
    }

    /**
     * 发布审计事件
     *
     * @param tenantId      租户ID
     * @param userId        用户ID
     * @param operationType 操作类型（BIND/UNBIND/VERIFY/ACTIVATE/DISABLE）
     * @param authMethod    认证方式（TOTP/HOTP/SMS/EMAIL/BACKUP_CODE）
     * @param deviceId      设备ID（可选）
     * @param result        操作结果（SUCCESS/FAILED/BLOCKED）
     * @param errorCode     错误码（失败时）
     * @param errorMessage  错误消息（失败时）
     * @param durationMs    操作耗时（毫秒，可选）
     */
    public void publishEvent(String tenantId,
                            String userId,
                            MfaOperationTypeEnum operationType,
                            String authMethod,
                            String deviceId,
                            String result,
                            String errorCode,
                            String errorMessage,
                            Long durationMs) {
        try {
            // 提取 IP 和 User-Agent
            String ipAddress = extractIpAddress();
            String userAgent = extractUserAgent();

            // 构建并发布事件
            MfaAuditEvent event = MfaAuditEvent.builder(source)
                .tenantId(tenantId)
                .userId(userId)
                .operationType(operationType)
                .authMethod(authMethod)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .deviceId(deviceId)
                .result(result)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .durationMs(durationMs)
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

            eventPublisher.publishEvent(event);

            if (log.isDebugEnabled()) {
                log.debug("MFA审计事件已发布: operationType={}, userId={}, result={}",
                    operationType != null ? operationType.getCode() : null, userId, result);
            }
        } catch (Exception e) {
            // 审计事件发布失败不应影响主流程，只记录错误日志
            log.error("发布MFA审计事件失败: operationType={}, userId={}, result={}",
                operationType != null ? operationType.getCode() : null, userId, result, e);
        }
    }

    /**
     * 发布成功事件（简化方法）
     *
     * @param tenantId      租户ID
     * @param userId        用户ID
     * @param operationType 操作类型（BIND/UNBIND/VERIFY/ACTIVATE/DISABLE）
     * @param authMethod    认证方式（TOTP/HOTP/SMS/EMAIL/BACKUP_CODE）
     * @param deviceId      设备ID（可选）
     */
    public void publishSuccess(String tenantId, String userId, MfaOperationTypeEnum operationType, String authMethod, String deviceId) {
        publishEvent(tenantId, userId, operationType, authMethod, deviceId, "SUCCESS", null, null, null);
    }

    /**
     * 发布失败事件（简化方法）
     *
     * @param tenantId      租户ID
     * @param userId        用户ID
     * @param operationType 操作类型（BIND/UNBIND/VERIFY/ACTIVATE/DISABLE）
     * @param authMethod    认证方式（TOTP/HOTP/SMS/EMAIL/BACKUP_CODE）
     * @param deviceId      设备ID（可选）
     * @param errorCode     错误码
     * @param errorMessage  错误消息
     */
    public void publishFailure(String tenantId, String userId, MfaOperationTypeEnum operationType, String authMethod,
                              String deviceId, String errorCode, String errorMessage) {
        publishEvent(tenantId, userId, operationType, authMethod, deviceId, "FAILED", errorCode, errorMessage, null);
    }

    /**
     * 发布锁定事件（简化方法）
     *
     * @param tenantId      租户ID
     * @param userId        用户ID
     * @param operationType 操作类型（BIND/UNBIND/VERIFY/ACTIVATE/DISABLE）
     * @param authMethod    认证方式（TOTP/HOTP/SMS/EMAIL/BACKUP_CODE）
     * @param deviceId      设备ID（可选）
     */
    public void publishBlocked(String tenantId, String userId, MfaOperationTypeEnum operationType, String authMethod, String deviceId) {
        publishEvent(tenantId, userId, operationType, authMethod, deviceId, "BLOCKED", "ACCOUNT_LOCKED", "账户已锁定", null);
    }

    /**
     * 提取客户端 IP 地址
     *
     * @return IP 地址，如果无法获取则返回 null
     */
    private String extractIpAddress() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }
            HttpServletRequest request = attributes.getRequest();

            // 优先从 X-Forwarded-For 获取（支持反向代理）
            String ip = request.getHeader("X-Forwarded-For");
            if (StringUtils.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
                // 多个 IP 时取第一个
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }

            // 尝试其他常见代理头
            ip = request.getHeader("Proxy-Client-IP");
            if (StringUtils.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }

            ip = request.getHeader("WL-Proxy-Client-IP");
            if (StringUtils.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }

            ip = request.getHeader("HTTP_CLIENT_IP");
            if (StringUtils.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }

            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
            if (StringUtils.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }

            // 最后使用 RemoteAddr
            return request.getRemoteAddr();
        } catch (Exception e) {
            log.debug("提取IP地址失败", e);
            return null;
        }
    }

    /**
     * 提取 User-Agent
     *
     * @return User-Agent，如果无法获取则返回 null
     */
    private String extractUserAgent() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }
            HttpServletRequest request = attributes.getRequest();
            return request.getHeader("User-Agent");
        } catch (Exception e) {
            log.debug("提取User-Agent失败", e);
            return null;
        }
    }
}
