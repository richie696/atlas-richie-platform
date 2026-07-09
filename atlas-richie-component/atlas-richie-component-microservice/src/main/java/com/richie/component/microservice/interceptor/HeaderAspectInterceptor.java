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
package com.richie.component.microservice.interceptor;

import com.richie.context.common.api.HeaderContextHolder;
import com.richie.contract.constant.GlobalConstants;
import com.richie.context.utils.data.Collections;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.stream.Collectors;


/**
 * OpenFeign 请求拦截器
 *
 * @author richie696
 * @version 1.0
 * @since 2022-05-16 11:20:48
 */
@Slf4j
@Aspect
@Order(1)
@Component
@RequiredArgsConstructor
public class HeaderAspectInterceptor implements IgnoreHeaderContent {

    /**
     * 切点：所有 Controller 的 public 方法。
     */
    @Pointcut("execution(public * com.richie..*.controller..*Controller.*(..))")
    private void useMethod() {
    }

    /**
     * 环绕增强：请求前将 Header 写入 HeaderContextHolder，请求后将部分 Header 写回响应并清理上下文。
     *
     * @param joinPoint 切点
     * @return 原方法返回值
     * @throws Throwable 原方法或切面逻辑抛出的异常
     */
    @Around("useMethod()")
    public Object recordLog(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取 Request 对象
        var requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        assert requestAttributes != null;
        var request = requestAttributes.getRequest();
        preHandle(request);
        Object result;
        try {
            result = joinPoint.proceed();
        } finally {
            postHandle(requestAttributes.getResponse());
        }
        return result;
    }

    /**
     * 请求前：将当前请求头（除忽略列表外）写入 HeaderContextHolder。
     *
     * @param request 当前 HTTP 请求
     */
    private void preHandle(@Nonnull HttpServletRequest request) {
        // 传递Request Header中的参数
        Collections.streamOf(request.getHeaderNames())
                .filter(o -> !IGNORE_HEADERS.contains(o.toLowerCase()))
                .collect(Collectors.toMap(key -> key, request::getHeader))
                .forEach(HeaderContextHolder::setHeader);
    }


    /**
     * 请求后：将 HeaderContextHolder 中的时区、语言、租户等写回响应头，并清理上下文。
     *
     * @param response 当前 HTTP 响应，可为 null
     */
    public void postHandle(HttpServletResponse response) {
        if (response == null) {
            return;
        }
        var timeFormat = HeaderContextHolder.getHeader(GlobalConstants.X_TIME_FORMAT_PATTERN);
        if (StringUtils.isNotBlank(timeFormat)) {
            response.setHeader(GlobalConstants.X_TIME_FORMAT_PATTERN, timeFormat);
        }
        var currencyFormat = HeaderContextHolder.getHeader(GlobalConstants.X_CURRENCY_FORMAT_PATTERN);
        if (StringUtils.isNotBlank(currencyFormat)) {
            response.setHeader(GlobalConstants.X_CURRENCY_FORMAT_PATTERN, currencyFormat);
        }
        var timezone = HeaderContextHolder.getHeader(GlobalConstants.X_RD_REQUEST_TIMEZONE);
        if (StringUtils.isNotBlank(timezone)) {
            response.setHeader(GlobalConstants.X_RD_REQUEST_TIMEZONE, timezone);
        }
        var language = HeaderContextHolder.getHeader(GlobalConstants.X_RD_REQUEST_LANGUAGE);
        if (StringUtils.isNotBlank(language)) {
            response.setHeader(GlobalConstants.X_RD_REQUEST_LANGUAGE, language);
        }
        var shopCode = HeaderContextHolder.getHeader(GlobalConstants.X_RD_REQUEST_SHOP_CODE);
        if (StringUtils.isNotBlank(shopCode)) {
            response.setHeader(GlobalConstants.X_RD_REQUEST_SHOP_CODE, shopCode);
        }
        var tenantId = HeaderContextHolder.getHeader(GlobalConstants.X_TENANT_ID);
        if (StringUtils.isNotBlank(tenantId)) {
            response.setHeader(GlobalConstants.X_TENANT_ID, tenantId);
        }
        var extra = HeaderContextHolder.getHeader(GlobalConstants.X_RD_REQUEST_EXTRA);
        if (StringUtils.isNotBlank(extra)) {
            response.setHeader(GlobalConstants.X_RD_REQUEST_EXTRA, extra);
        }
        HeaderContextHolder.removeContext();
    }
}


