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
