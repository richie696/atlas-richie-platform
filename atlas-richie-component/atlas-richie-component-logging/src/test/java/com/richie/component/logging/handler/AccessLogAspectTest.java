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
package com.richie.component.logging.handler;

import com.richie.component.dao.snowflake.IdBuilder;
import com.richie.component.logging.annotations.AccessLog;
import com.richie.component.logging.config.OperateLogProperties;
import com.richie.component.logging.domain.AccessLogInfo;
import com.richie.component.logging.enums.RecordTypeEnum;
import com.richie.component.logging.service.AccessLogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AccessLogAspect}.
 * <p>
 * Focuses on the {@code Stopwatch} migration: elapsedTime must be monotonic, accurate,
 * and immune to wall-clock anomalies (NTP jumps, system clock skew).
 */
class AccessLogAspectTest {

    private AccessLogAspect aspect;
    private OperateLogProperties properties;
    private QueueHandler queueHandler;

    @BeforeEach
    void setUp() {
        properties = new OperateLogProperties();
        properties.setEnable(true);
        properties.setRecordType(RecordTypeEnum.MQ);
        properties.setMqTopicName("test-access-log");
        properties.setDbPersistent(false);
        properties.setEnableGlobalAdvice(false);
        properties.setPrintException(false);

        queueHandler = mock(QueueHandler.class);
        AccessLogService accessLogService = mock(AccessLogService.class);
        IdBuilder idBuilder = mock(IdBuilder.class);
        when(idBuilder.nextId()).thenReturn(12345L);

        aspect = new AccessLogAspect(properties, queueHandler, accessLogService, idBuilder, mock());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        ServletRequestAttributes attrs = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attrs);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void elapsedTime_approximatelyMatchesActualDelay_afterMigrationToStopwatch() throws Throwable {
        Method targetMethod = TestController.class.getDeclaredMethod("handle");

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(targetMethod);
        when(signature.getParameterNames()).thenReturn(new String[0]);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);

        when(joinPoint.proceed()).thenAnswer(invocation -> {
            Thread.sleep(50);
            return "result";
        });

        aspect.recordLog(joinPoint);

        ArgumentCaptor<AccessLogInfo> captor = ArgumentCaptor.forClass(AccessLogInfo.class);
        verify(queueHandler).sendMessage(anyString(), captor.capture());
        AccessLogInfo captured = captor.getValue();
        assertThat(captured.getElapsedTime())
                .as("elapsedTime should be at least 40ms (50ms sleep - 10ms jitter margin)")
                .isGreaterThanOrEqualTo(40L)
                .isLessThan(5000L);
    }

    @Test
    void elapsedTime_isNonNegative_evenForImmediateReturn() throws Throwable {
        Method targetMethod = TestController.class.getDeclaredMethod("handle");

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(targetMethod);
        when(signature.getParameterNames()).thenReturn(new String[0]);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn("immediate");

        aspect.recordLog(joinPoint);

        ArgumentCaptor<AccessLogInfo> captor = ArgumentCaptor.forClass(AccessLogInfo.class);
        verify(queueHandler).sendMessage(anyString(), captor.capture());
        assertThat(captor.getValue().getElapsedTime())
                .as("Stopwatch is monotonic and must never produce negative elapsed")
                .isGreaterThanOrEqualTo(0L);
    }

    @Test
    void elapsedTime_isStillRecorded_onExceptionPath() throws Throwable {
        Method targetMethod = TestController.class.getDeclaredMethod("handle");

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(targetMethod);
        when(signature.getParameterNames()).thenReturn(new String[0]);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            Thread.sleep(20);
            throw new RuntimeException("boom");
        });

        assertThatThrownBy(() -> aspect.recordLog(joinPoint))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");

        ArgumentCaptor<AccessLogInfo> captor = ArgumentCaptor.forClass(AccessLogInfo.class);
        verify(queueHandler).sendMessage(anyString(), captor.capture());
        assertThat(captor.getValue().getElapsedTime())
                .as("elapsedTime should also be captured on exception path (>= 15ms)")
                .isGreaterThanOrEqualTo(15L);
    }

    @Test
    void recordLog_disabled_skipsEverything() throws Throwable {
        properties.setEnable(false);

        Method targetMethod = TestController.class.getDeclaredMethod("handle");
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(targetMethod);
        when(signature.getParameterNames()).thenReturn(new String[0]);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.recordLog(joinPoint);

        assertThat(result).isEqualTo("ok");
        verify(queueHandler, never()).sendMessage(anyString(), any());
    }

    static class TestController {
        @AccessLog("test")
        public String handle() {
            return "ok";
        }
    }
}