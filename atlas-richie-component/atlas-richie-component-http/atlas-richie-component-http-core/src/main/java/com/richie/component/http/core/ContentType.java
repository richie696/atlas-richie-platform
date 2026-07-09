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
package com.richie.component.http.core;

/**
 * 请求内容类型与 MIME 的映射。
 * <p>
 * 调用方通过 {@link HttpRequest#asJson()} / {@link HttpRequest#asXml()} 等方法表达业务意图，
 * 由实现类读取此枚举来设置正确的 Content-Type 头。
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
enum ContentType {

    JSON("application/json; charset=utf-8"),
    XML("application/xml; charset=utf-8"),
    SOAP("application/soap+xml"),
    FORM("application/x-www-form-urlencoded"),
    MULTIPART("multipart/form-data"),
    DEFAULT("application/json; charset=utf-8");

    private final String mime;

    ContentType(String mime) { this.mime = mime; }

    String mime() { return mime; }

}
