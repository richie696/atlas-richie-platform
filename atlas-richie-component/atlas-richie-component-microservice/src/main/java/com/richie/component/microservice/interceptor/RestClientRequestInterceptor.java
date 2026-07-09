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

import com.richie.context.utils.data.Collections;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.util.stream.Collectors;

/**
 * RestClient 请求拦截器，用于跨服务传递必要的请求头
 *
 * @author richie696
 * @version 1.0
 * @since 2025/10/28
 */
@Slf4j
@Component
public class RestClientRequestInterceptor implements ClientHttpRequestInterceptor, IgnoreHeaderContent {

    /**
     * 默认构造函数（供 Spring 使用）。
     */
    public RestClientRequestInterceptor() {
    }

    /**
     * 初始化时打印日志。
     */
    @PostConstruct
    public void init() {
        log.info("RestClient 请求拦截器初始化完成");
    }

    /**
     * 将当前 HTTP 请求的 Header（除忽略列表外）透传到 RestClient 请求，并执行调用。
     *
     * @param request    HTTP 请求
     * @param body       请求体
     * @param execution  执行链
     * @return 下游响应
     * @throws IOException 执行请求时发生 IO 异常
     */
    @Override
    @Nonnull
    public ClientHttpResponse intercept(@Nonnull HttpRequest request, @Nonnull byte[] body, @Nonnull ClientHttpRequestExecution execution) throws IOException {
        var httpServletRequest = getHttpServletRequest();
        if (httpServletRequest == null) {
            log.debug("RestClient interceptor request = null");
            return execution.execute(request, body);
        }

        // 传递 Request Header 中的参数（按忽略清单过滤）
        Collections.streamOf(httpServletRequest.getHeaderNames())
                .filter(o -> !IgnoreHeaderContent.IGNORE_HEADERS.contains(o.toLowerCase()))
                .collect(Collectors.toMap(key -> key, httpServletRequest::getHeader))
                .forEach(request.getHeaders()::set);

        if (request.getMethod() == HttpMethod.GET) {
            var headers = request.getHeaders();
            if (headers.containsHeader(HttpHeaders.CONTENT_TYPE)) {
                log.debug("移除 GET 请求的 Content-Type 头: {}", headers.getFirst(HttpHeaders.CONTENT_TYPE));
                headers.remove(HttpHeaders.CONTENT_TYPE);
            }
        }
        return execution.execute(request, body);
    }

    /**
     * 从当前请求上下文获取 HttpServletRequest。
     *
     * @return 当前请求，非 Web 或非 Servlet 上下文时为 null
     */
    private HttpServletRequest getHttpServletRequest() {
        var requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        return attrs.getRequest();
    }
}


