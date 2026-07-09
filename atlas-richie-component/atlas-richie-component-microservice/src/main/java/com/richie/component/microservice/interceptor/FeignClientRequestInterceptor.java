/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.microservice.interceptor;

import com.richie.context.utils.data.Collections;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;
import java.util.stream.Collectors;


/**
 * Feign客户端请求拦截器
 *
 * @author richie696
 * @version 1.0
 * @since 2023-09-17 21:46:37
 */
@Slf4j
public class FeignClientRequestInterceptor implements RequestInterceptor, IgnoreHeaderContent {

    /**
     * 默认构造函数（供 Spring 使用）。
     */
    public FeignClientRequestInterceptor() {
    }

    /**
     * 初始化时打印日志。
     */
    @PostConstruct
    public void init() {
        log.info("FeignClient 请求拦截器初始化完成");
    }

    /**
     * 将当前 HTTP 请求的 Header（除忽略列表外）透传到 Feign 请求模板。
     *
     * @param requestTemplate Feign 请求模板
     */
    @Override
    public void apply(RequestTemplate requestTemplate) {
        var request = getHttpServletRequest();
        if (request == null) {
            log.debug("Feign client interceptor request = null");
            return;
        }
        // 传递Request Header中的参数
        Collections.streamOf(request.getHeaderNames())
                .filter(o -> !IgnoreHeaderContent.IGNORE_HEADERS.contains(o.toLowerCase()))
                .collect(Collectors.toMap(key -> key, request::getHeader))
                .forEach(requestTemplate::header);
    }

    /**
     * 从当前请求上下文获取 HttpServletRequest。
     *
     * @return 当前请求，非 Web 上下文时为 null
     */
    private HttpServletRequest getHttpServletRequest() {
        var requestAttributes = RequestContextHolder.getRequestAttributes();
        if (Objects.isNull(requestAttributes)) {
            return null;
        }
        return ((ServletRequestAttributes) requestAttributes).getRequest();
    }
}

