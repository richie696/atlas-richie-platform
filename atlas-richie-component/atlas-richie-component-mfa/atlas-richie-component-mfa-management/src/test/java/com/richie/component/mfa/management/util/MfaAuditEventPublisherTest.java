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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MfaAuditEventPublisherTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private HttpServletRequest request;

    @Captor
    private ArgumentCaptor<MfaAuditEvent> eventCaptor;

    private MfaAuditEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new MfaAuditEventPublisher(eventPublisher, this);
    }

    @Test
    void publishSuccess_emitsAuditEvent() {
        publisher.publishSuccess("t1", "u1", MfaOperationTypeEnum.BIND, "TOTP", "device-1");

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        MfaAuditEvent event = eventCaptor.getValue();
        assertThat(event.getUserId()).isEqualTo("u1");
        assertThat(event.getResult()).isEqualTo("SUCCESS");
        assertThat(event.getOperationType()).isEqualTo(MfaOperationTypeEnum.BIND);
    }

    @Test
    void publishFailure_emitsFailedResult() {
        publisher.publishFailure("t1", "u1", MfaOperationTypeEnum.VERIFY, "TOTP", null,
                "MFA_CODE_INVALID", "验证码错误");

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getResult()).isEqualTo("FAILED");
        assertThat(eventCaptor.getValue().getErrorCode()).isEqualTo("MFA_CODE_INVALID");
    }

    @Test
    void publishBlocked_emitsBlockedResult() {
        publisher.publishBlocked("t1", "u1", MfaOperationTypeEnum.VERIFY, "TOTP", null);

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getResult()).isEqualTo("BLOCKED");
        assertThat(eventCaptor.getValue().getErrorCode()).isEqualTo("ACCOUNT_LOCKED");
    }

    @Test
    void publishSuccess_extractsForwardedIpAndUserAgent() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1, 10.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("JUnit-Agent");
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);

        try (MockedStatic<RequestContextHolder> context = mockStatic(RequestContextHolder.class)) {
            context.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);

            publisher.publishSuccess("t1", "u1", MfaOperationTypeEnum.BIND, "TOTP", "device-1");
        }

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getIpAddress()).isEqualTo("203.0.113.1");
        assertThat(eventCaptor.getValue().getUserAgent()).isEqualTo("JUnit-Agent");
    }

    @Test
    void publishSuccess_fallsBackToRemoteAddr() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("Proxy-Client-IP")).thenReturn("unknown");
        when(request.getHeader("WL-Proxy-Client-IP")).thenReturn(null);
        when(request.getHeader("HTTP_CLIENT_IP")).thenReturn(null);
        when(request.getHeader("HTTP_X_FORWARDED_FOR")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);

        try (MockedStatic<RequestContextHolder> context = mockStatic(RequestContextHolder.class)) {
            context.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);

            publisher.publishSuccess("t1", "u1", MfaOperationTypeEnum.BIND, "TOTP", null);
        }

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getIpAddress()).isEqualTo("127.0.0.1");
    }

    @Test
    void publishSuccess_usesProxyClientIpHeader() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("unknown");
        when(request.getHeader("Proxy-Client-IP")).thenReturn("198.51.100.2");
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);

        try (MockedStatic<RequestContextHolder> context = mockStatic(RequestContextHolder.class)) {
            context.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);

            publisher.publishSuccess("t1", "u1", MfaOperationTypeEnum.BIND, "TOTP", null);
        }

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getIpAddress()).isEqualTo("198.51.100.2");
    }

    @Test
    void publishSuccess_usesWlProxyClientIpHeader() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("unknown");
        when(request.getHeader("Proxy-Client-IP")).thenReturn(null);
        when(request.getHeader("WL-Proxy-Client-IP")).thenReturn("203.0.113.5");
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);

        try (MockedStatic<RequestContextHolder> context = mockStatic(RequestContextHolder.class)) {
            context.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);

            publisher.publishSuccess("t1", "u1", MfaOperationTypeEnum.BIND, "TOTP", null);
        }

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getIpAddress()).isEqualTo("203.0.113.5");
    }

    @Test
    void publishSuccess_usesHttpClientIpHeader() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("unknown");
        when(request.getHeader("Proxy-Client-IP")).thenReturn(null);
        when(request.getHeader("WL-Proxy-Client-IP")).thenReturn(null);
        when(request.getHeader("HTTP_CLIENT_IP")).thenReturn("198.51.100.10");
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);

        try (MockedStatic<RequestContextHolder> context = mockStatic(RequestContextHolder.class)) {
            context.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);

            publisher.publishSuccess("t1", "u1", MfaOperationTypeEnum.BIND, "TOTP", null);
        }

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getIpAddress()).isEqualTo("198.51.100.10");
    }

    @Test
    void publishSuccess_usesHttpXForwardedForHeader() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("unknown");
        when(request.getHeader("Proxy-Client-IP")).thenReturn(null);
        when(request.getHeader("WL-Proxy-Client-IP")).thenReturn(null);
        when(request.getHeader("HTTP_CLIENT_IP")).thenReturn(null);
        when(request.getHeader("HTTP_X_FORWARDED_FOR")).thenReturn("192.0.2.50");
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);

        try (MockedStatic<RequestContextHolder> context = mockStatic(RequestContextHolder.class)) {
            context.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);

            publisher.publishSuccess("t1", "u1", MfaOperationTypeEnum.BIND, "TOTP", null);
        }

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getIpAddress()).isEqualTo("192.0.2.50");
    }

    @Test
    void publishEvent_swallowsPublisherExceptions() {
        doThrow(new RuntimeException("bus down")).when(eventPublisher).publishEvent(any());

        publisher.publishSuccess("t1", "u1", MfaOperationTypeEnum.BIND, "TOTP", null);

        verify(eventPublisher).publishEvent(any());
    }
}
