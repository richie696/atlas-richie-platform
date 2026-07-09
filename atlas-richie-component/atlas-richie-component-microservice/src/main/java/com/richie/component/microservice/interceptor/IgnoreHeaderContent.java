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

import java.util.Set;

/**
 * 请求头透传时需忽略的 Header 名称集合。
 * <p>
 * 用于 Feign/RestClient 拦截器与 HeaderAspectInterceptor，避免将 host、content-length 等不应透传的头部带到下游。
 *
 * @author richie696
 * @since 2022-05-16
 */
public interface IgnoreHeaderContent {

    /** 透传时需忽略的 Header 名称（小写） */
    Set<String> IGNORE_HEADERS = Set.of(
            "host",
            "content-type",
            "content-length",
            "connection",
            "date",
            "transfer-encoding",
            "vary",
            "x-android-received-millis",
            "x-android-response-source",
            "x-android-selected-protocol",
            "x-android-sent-millis",
            "client-via",
            "cache-control",
            "expires",
            "server",
            "accept",
            "accept-encoding",
            "accept-language",
            "referer",
            "user-agent",
            "origin",
            "x-client-id",
            "x-client-timestamp",
            "x-client-public-key",
            "x-gateway-keyid",
            "x-encrypted-data",
            "x-response-encrypted",
            "access-control-allow-origin",
            "access-control-allow-methods",
            "access-control-allow-headers",
            "access-control-expose-headers",
            "access-control-max-age",
            "access-control-allow-credentials"
    );

}
