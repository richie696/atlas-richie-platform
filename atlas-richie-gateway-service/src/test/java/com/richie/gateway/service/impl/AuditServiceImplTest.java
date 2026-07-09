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
package com.richie.gateway.service.impl;

import com.richie.component.redis.streammq.StreamMQ;
import com.richie.component.redis.streammq.function.StreamFunction;
import com.richie.contract.gateway.model.OAuth2AuditEvent;
import com.richie.contract.gateway.model.OAuth2AuditEventType;
import com.richie.gateway.config.GatewayConfig;
import com.richie.gateway.constants.GatewayRedisKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceImplTest {

    private static final String CLIENT_ID = "test-client-001";
    private static final String CLIENT_NAME = "Test Client";
    private static final String IP = "192.168.1.100";
    private static final String USER_AGENT = "Mozilla/5.0";
    private static final String TOKEN_ID = "token-abc123";
    private static final String ERROR_CODE = "invalid_grant";
    private static final String ERROR_MSG = "Client authentication failed";
    private static final String PATH = "/api/resource";
    private static final String METHOD = "GET";
    private static final String REASON = "token_expired";
    private static final String ACTIVITY_TYPE = "brute_force";
    private static final String DETAILS = "Multiple failed login attempts";
    private static final String TRACE_ID = "trace-12345";

    @Mock
    private GatewayConfig gatewayConfig;

    @Mock
    private StreamFunction streamFunction;

    private MockedStatic<StreamMQ> streamMQMockedStatic;

    private AuditServiceImpl auditService;

    @BeforeEach
    void setUp() {
        streamMQMockedStatic = mockStatic(StreamMQ.class);
        streamMQMockedStatic.when(StreamMQ::stream).thenReturn(streamFunction);
        auditService = new AuditServiceImpl(gatewayConfig);
    }

    @AfterEach
    void tearDown() {
        if (streamMQMockedStatic != null) {
            streamMQMockedStatic.close();
        }
        MDC.clear();
    }

    // ==================== publishAuditEvent tests (indirect) ====================

    @Nested
    @DisplayName("publishAuditEvent 审计事件发布")
    class PublishAuditEventTests {

        @Test
        @DisplayName("审计开关关闭时不发布事件")
        void publishAuditEvent_auditDisabled_returnsEarly() {
            when(gatewayConfig.isAuditEnabled()).thenReturn(false);

            OAuth2AuditEvent event = OAuth2AuditEvent.builder()
                    .eventType(OAuth2AuditEventType.TOKEN_ISSUED)
                    .clientId(CLIENT_ID)
                    .build();

            auditService.auditAuthEvent(event);

            verify(gatewayConfig).isAuditEnabled();
            verify(streamFunction, never()).publish(any(), any());
        }

        @Test
        @DisplayName("审计开关开启时发布事件到 StreamMQ")
        void publishAuditEvent_auditEnabled_publishesEvent() {
            when(gatewayConfig.isAuditEnabled()).thenReturn(true);
            when(streamFunction.publish(any(), any())).thenReturn("msg-id-123");

            OAuth2AuditEvent event = OAuth2AuditEvent.builder()
                    .eventType(OAuth2AuditEventType.TOKEN_ISSUED)
                    .clientId(CLIENT_ID)
                    .ip(IP)
                    .build();

            auditService.auditAuthEvent(event);

            verify(streamFunction).publish(eq(GatewayRedisKey.OAUTH2_AUDIT_EVENTS.getKey()), eq(event));
        }

        @Test
        @DisplayName("事件 timestamp 为 null 时自动设置")
        void publishAuditEvent_nullTimestamp_setsTimestamp() {
            when(gatewayConfig.isAuditEnabled()).thenReturn(true);
            when(streamFunction.publish(any(), any())).thenReturn("msg-id-123");

            OAuth2AuditEvent event = OAuth2AuditEvent.builder()
                    .eventType(OAuth2AuditEventType.TOKEN_ISSUED)
                    .clientId(CLIENT_ID)
                    .timestamp(null)
                    .build();

            auditService.auditAuthEvent(event);

            assertThat(event.getTimestamp()).isNotNull();
            assertThat(event.getTimestamp()).isBeforeOrEqualTo(OffsetDateTime.now());
        }

        @Test
        @DisplayName("事件 requestId 为 null 时从 MDC 获取")
        void publishAuditEvent_nullRequestId_setsFromMDC() {
            when(gatewayConfig.isAuditEnabled()).thenReturn(true);
            when(streamFunction.publish(any(), any())).thenReturn("msg-id-123");
            MDC.put("traceId", TRACE_ID);

            OAuth2AuditEvent event = OAuth2AuditEvent.builder()
                    .eventType(OAuth2AuditEventType.TOKEN_ISSUED)
                    .clientId(CLIENT_ID)
                    .requestId(null)
                    .build();

            auditService.auditAuthEvent(event);

            assertThat(event.getRequestId()).isEqualTo(TRACE_ID);
        }

        @Test
        @DisplayName("StreamMQ.publish 抛出异常时不传播")
        void publishAuditEvent_publishThrowsException_doesNotPropagate() {
            when(gatewayConfig.isAuditEnabled()).thenReturn(true);
            when(streamFunction.publish(any(), any())).thenThrow(new RuntimeException("Redis connection failed"));

            OAuth2AuditEvent event = OAuth2AuditEvent.builder()
                    .eventType(OAuth2AuditEventType.TOKEN_ISSUED)
                    .clientId(CLIENT_ID)
                    .build();

            auditService.auditAuthEvent(event);
        }
    }

    // ==================== auditAuthEvent ====================

    @Nested
    @DisplayName("auditAuthEvent 认证事件审计")
    class AuditAuthEventTests {

        @Test
        @DisplayName("正常发布认证事件")
        void auditAuthEvent_validEvent_publishesEvent() {
            when(gatewayConfig.isAuditEnabled()).thenReturn(true);
            when(streamFunction.publish(any(), any())).thenReturn("msg-id");

            OAuth2AuditEvent event = OAuth2AuditEvent.builder()
                    .eventType(OAuth2AuditEventType.TOKEN_ISSUED)
                    .clientId(CLIENT_ID)
                    .build();

            auditService.auditAuthEvent(event);

            verify(streamFunction).publish(eq(GatewayRedisKey.OAUTH2_AUDIT_EVENTS.getKey()), eq(event));
        }
    }

    // ==================== auditAccessEvent ====================

    @Nested
    @DisplayName("auditAccessEvent 访问事件审计")
    class AuditAccessEventTests {

        @Test
        @DisplayName("正常发布访问事件")
        void auditAccessEvent_validEvent_publishesEvent() {
            when(gatewayConfig.isAuditEnabled()).thenReturn(true);
            when(streamFunction.publish(any(), any())).thenReturn("msg-id");

            OAuth2AuditEvent event = OAuth2AuditEvent.builder()
                    .eventType(OAuth2AuditEventType.ACCESS_GRANTED)
                    .clientId(CLIENT_ID)
                    .path(PATH)
                    .method(METHOD)
                    .build();

            auditService.auditAccessEvent(event);

            verify(streamFunction).publish(eq(GatewayRedisKey.OAUTH2_AUDIT_EVENTS.getKey()), eq(event));
        }
    }

    // ==================== auditSuspiciousActivity (event overload) ====================

    @Nested
    @DisplayName("auditSuspiciousActivity 可疑活动审计（事件入参）")
    class AuditSuspiciousActivityEventTests {

        @Test
        @DisplayName("正常发布可疑活动事件")
        void auditSuspiciousActivity_validEvent_publishesEvent() {
            when(gatewayConfig.isAuditEnabled()).thenReturn(true);
            when(streamFunction.publish(any(), any())).thenReturn("msg-id");

            OAuth2AuditEvent event = OAuth2AuditEvent.builder()
                    .eventType(OAuth2AuditEventType.SUSPICIOUS_ACTIVITY)
                    .clientId(CLIENT_ID)
                    .ip(IP)
                    .activityType(ACTIVITY_TYPE)
                    .build();

            auditService.auditSuspiciousActivity(event);

            verify(streamFunction).publish(eq(GatewayRedisKey.OAUTH2_AUDIT_EVENTS.getKey()), eq(event));
        }
    }

    // ==================== auditTokenIssued ====================

    @Nested
    @DisplayName("auditTokenIssued 令牌颁发审计")
    class AuditTokenIssuedTests {

        @Test
        @DisplayName("正常审计令牌颁发")
        void auditTokenIssued_buildsCorrectEvent() {
            when(gatewayConfig.isAuditEnabled()).thenReturn(true);
            when(streamFunction.publish(any(), any())).thenReturn("msg-id");
            MDC.put("traceId", TRACE_ID);

            auditService.auditTokenIssued(CLIENT_ID, CLIENT_NAME, IP, USER_AGENT, TOKEN_ID, 3600L);

            verify(streamFunction).publish(eq(GatewayRedisKey.OAUTH2_AUDIT_EVENTS.getKey()), any(OAuth2AuditEvent.class));
        }

        @Test
        @DisplayName("令牌颁发事件内容正确")
        void auditTokenIssued_eventContentCorrect() {
            when(gatewayConfig.isAuditEnabled()).thenReturn(true);
            when(streamFunction.publish(any(), any())).thenReturn("msg-id");

            auditService.auditTokenIssued(CLIENT_ID, CLIENT_NAME, IP, USER_AGENT, TOKEN_ID, 3600L);

            assertThat(streamFunction.publish(eq(GatewayRedisKey.OAUTH2_AUDIT_EVENTS.getKey()), any(OAuth2AuditEvent.class)));
        }
    }

    // ==================== auditTokenIssueFailed ====================

    @Nested
    @DisplayName("auditTokenIssueFailed 令牌颁发失败审计")
    class AuditTokenIssueFailedTests {

        @Test
        @DisplayName("正常审计令牌颁发失败")
        void auditTokenIssueFailed_buildsCorrectEvent() {
            when(gatewayConfig.isAuditEnabled()).thenReturn(true);
            when(streamFunction.publish(any(), any())).thenReturn("msg-id");

            auditService.auditTokenIssueFailed(CLIENT_ID, IP, USER_AGENT, ERROR_CODE, ERROR_MSG);

            verify(streamFunction).publish(eq(GatewayRedisKey.OAUTH2_AUDIT_EVENTS.getKey()), any(OAuth2AuditEvent.class));
        }
    }

    // ==================== auditTokenRefreshed ====================

    @Nested
    @DisplayName("auditTokenRefreshed 令牌刷新审计")
    class AuditTokenRefreshedTests {

        @Test
        @DisplayName("正常审计令牌刷新")
        void auditTokenRefreshed_buildsCorrectEvent() {
            when(gatewayConfig.isAuditEnabled()).thenReturn(true);
            when(streamFunction.publish(any(), any())).thenReturn("msg-id");

            auditService.auditTokenRefreshed(CLIENT_ID, IP, USER_AGENT, TOKEN_ID, 7200L);

            verify(streamFunction).publish(eq(GatewayRedisKey.OAUTH2_AUDIT_EVENTS.getKey()), any(OAuth2AuditEvent.class));
        }
    }

    // ==================== auditTokenRefreshFailed ====================

    @Nested
    @DisplayName("auditTokenRefreshFailed 令牌刷新失败审计")
    class AuditTokenRefreshFailedTests {

        @Test
        @DisplayName("正常审计令牌刷新失败")
        void auditTokenRefreshFailed_buildsCorrectEvent() {
            when(gatewayConfig.isAuditEnabled()).thenReturn(true);
            when(streamFunction.publish(any(), any())).thenReturn("msg-id");

            auditService.auditTokenRefreshFailed(CLIENT_ID, IP, USER_AGENT, ERROR_CODE, ERROR_MSG);

            verify(streamFunction).publish(eq(GatewayRedisKey.OAUTH2_AUDIT_EVENTS.getKey()), any(OAuth2AuditEvent.class));
        }
    }

    // ==================== auditTokenRevoked ====================

    @Nested
    @DisplayName("auditTokenRevoked 令牌撤销审计")
    class AuditTokenRevokedTests {

        @Test
        @DisplayName("正常审计令牌撤销")
        void auditTokenRevoked_buildsCorrectEvent() {
            when(gatewayConfig.isAuditEnabled()).thenReturn(true);
            when(streamFunction.publish(any(), any())).thenReturn("msg-id");

            auditService.auditTokenRevoked(CLIENT_ID, TOKEN_ID, IP, USER_AGENT, REASON);

            verify(streamFunction).publish(eq(GatewayRedisKey.OAUTH2_AUDIT_EVENTS.getKey()), any(OAuth2AuditEvent.class));
        }
    }

    // ==================== auditAccessGranted ====================

    @Nested
    @DisplayName("auditAccessGranted 访问授权审计")
    class AuditAccessGrantedTests {

        @Test
        @DisplayName("正常审计访问授权")
        void auditAccessGranted_buildsCorrectEvent() {
            when(gatewayConfig.isAuditEnabled()).thenReturn(true);
            when(streamFunction.publish(any(), any())).thenReturn("msg-id");

            auditService.auditAccessGranted(CLIENT_ID, PATH, METHOD, IP, USER_AGENT);

            verify(streamFunction).publish(eq(GatewayRedisKey.OAUTH2_AUDIT_EVENTS.getKey()), any(OAuth2AuditEvent.class));
        }
    }

    // ==================== auditAccessDenied ====================

    @Nested
    @DisplayName("auditAccessDenied 访问拒绝审计")
    class AuditAccessDeniedTests {

        @Test
        @DisplayName("正常审计访问拒绝")
        void auditAccessDenied_buildsCorrectEvent() {
            when(gatewayConfig.isAuditEnabled()).thenReturn(true);
            when(streamFunction.publish(any(), any())).thenReturn("msg-id");

            auditService.auditAccessDenied(CLIENT_ID, PATH, METHOD, IP, USER_AGENT, REASON, ERROR_CODE, ERROR_MSG);

            verify(streamFunction).publish(eq(GatewayRedisKey.OAUTH2_AUDIT_EVENTS.getKey()), any(OAuth2AuditEvent.class));
        }
    }

    // ==================== auditSuspiciousActivity (params overload) ====================

    @Nested
    @DisplayName("auditSuspiciousActivity 可疑活动审计（参数入参）")
    class AuditSuspiciousActivityParamsTests {

        @Test
        @DisplayName("正常审计可疑活动")
        void auditSuspiciousActivity_buildsCorrectEvent() {
            when(gatewayConfig.isAuditEnabled()).thenReturn(true);
            when(streamFunction.publish(any(), any())).thenReturn("msg-id");

            auditService.auditSuspiciousActivity(CLIENT_ID, IP, ACTIVITY_TYPE, DETAILS);

            verify(streamFunction).publish(eq(GatewayRedisKey.OAUTH2_AUDIT_EVENTS.getKey()), any(OAuth2AuditEvent.class));
        }
    }
}
