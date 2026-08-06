/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.gateway.web.error;

import cn.richie696.component.i18n.config.I18nProperties;
import cn.richie696.contract.constant.GlobalConstants;
import cn.richie696.gateway.error.GatewayErrorCode;
import cn.richie696.gateway.error.GatewayErrorRegistry;
import cn.richie696.gateway.filter.common.infrastructure.RequestIdGlobalFilter;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

/** Public, dependency-free documentation for the gateway's stable error protocol. */
@Component
public class ErrorPageRouter {
    private final MessageSource messages;
    private final I18nProperties i18nProperties;

    public ErrorPageRouter(MessageSource messages, I18nProperties i18nProperties) {
        this.messages = messages;
        this.i18nProperties = i18nProperties;
    }

    @Bean
    RouterFunction<ServerResponse> gatewayErrorPages() {
        return RouterFunctions.route(GET(GatewayErrorRegistry.INDEX_PATH), this::index)
                .andRoute(GET(GatewayErrorRegistry.JSON_PATH), this::json)
                .andRoute(GET(GatewayErrorRegistry.INDEX_PATH + "/{code}"), this::detail);
    }

    private Mono<ServerResponse> index(ServerRequest request) {
        Locale locale = locale(request);
        StringBuilder rows = new StringBuilder();
        for (GatewayErrorCode code : GatewayErrorRegistry.all()) {
            rows.append("<tr><td><a href=\"").append(GatewayErrorRegistry.helpUrl(code)).append("\">")
                    .append(escape(code.getCode())).append("</a></td><td>").append(code.getHttpStatus())
                    .append("</td><td>").append(escape(message(code.getI18nKey() + ".meaning", locale)))
                    .append("</td><td>").append(code.isRetryable() ? message("gateway.error.retry.yes", locale)
                            : message("gateway.error.retry.no", locale)).append("</td></tr>");
        }
        String html = head(message("gateway.error.index.title", locale)) + "<h1>"
                + escape(message("gateway.error.index.title", locale)) + "</h1><p>"
                + escape(message("gateway.error.index.subtitle", locale))
                + "</p><table><thead><tr><th>" + escape(message("gateway.error.col.code", locale))
                + "</th><th>HTTP</th><th>" + escape(message("gateway.error.col.meaning", locale))
                + "</th><th>" + escape(message("gateway.error.col.retry", locale))
                + "</th></tr></thead><tbody>" + rows + "</tbody></table>" + foot();
        return html(request, 200, html);
    }

    private Mono<ServerResponse> detail(ServerRequest request) {
        GatewayErrorCode code = GatewayErrorRegistry.byCode(request.pathVariable("code"));
        Locale locale = locale(request);
        if (code == null) {
            String html = head(message("gateway.error.notFound.title", locale)) + "<h1>"
                    + escape(message("gateway.error.notFound.title", locale)) + "</h1><p>"
                    + escape(message("gateway.error.notFound.hint", locale)) + "</p>" + back(locale) + foot();
            return html(request, 404, html);
        }
        String prefix = code.getI18nKey();
        String html = head(code.getCode()) + "<h1>" + escape(code.getCode()) + " · "
                + escape(message(prefix + ".meaning", locale)) + "</h1><dl>"
                + row(message("gateway.error.label.httpStatus", locale), "HTTP " + code.getHttpStatus())
                + row(message("gateway.error.label.cause", locale), message(prefix + ".cause", locale))
                + row(message("gateway.error.label.investigate", locale), message(prefix + ".investigate", locale))
                + row(message("gateway.error.label.retry", locale), code.isRetryable()
                        ? message("gateway.error.retry.yes", locale) : message("gateway.error.retry.no", locale))
                + "</dl>" + back(locale) + foot();
        return html(request, 200, html);
    }

    private Mono<ServerResponse> json(ServerRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("codes", GatewayErrorRegistry.all().stream().map(code -> Map.of(
                "code", code.getCode(), "httpStatus", code.getHttpStatus(), "retryable", code.isRetryable(),
                "helpUrl", GatewayErrorRegistry.helpUrl(code))).toList());
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
                .header(RequestIdGlobalFilter.HEADER_NAME, requestId(request)).bodyValue(response);
    }

    private Mono<ServerResponse> html(ServerRequest request, int status, String body) {
        return ServerResponse.status(status).contentType(MediaType.TEXT_HTML)
                .header(RequestIdGlobalFilter.HEADER_NAME, requestId(request)).bodyValue(body);
    }
    private Locale locale(ServerRequest request) {
        String language = request.headers().firstHeader(GlobalConstants.X_RD_REQUEST_LANGUAGE);
        return language == null || language.isBlank() ? i18nProperties.getDefaultLocale()
                : Locale.forLanguageTag(language.replace('_', '-'));
    }
    private String requestId(ServerRequest request) {
        String id = request.exchange().getAttribute(RequestIdGlobalFilter.ATTRIBUTE_KEY);
        return id == null ? "" : id;
    }
    private String message(String key, Locale locale) { return messages.getMessage(key, null, key, locale); }
    private static String row(String label, String value) { return "<dt>" + escape(label) + "</dt><dd>" + escape(value) + "</dd>"; }
    private static String back(Locale locale) { return "<p><a href=\"/gateway/errors\">← " + escape(locale.getLanguage().equals("zh") ? "返回错误码清单" : "Back to error codes") + "</a></p>"; }
    private static String head(String title) { return "<!doctype html><html><head><meta charset=\"utf-8\"><title>" + escape(title) + "</title><style>body{font:16px system-ui;margin:3rem auto;max-width:1000px;padding:0 1rem;color:#172033}table{border-collapse:collapse;width:100%}th,td{border:1px solid #d8dee9;padding:.75rem;text-align:left}th{background:#f4f7fb}a{color:#1264d6}dt{font-weight:700;margin-top:1rem}dd{margin:.25rem 0}</style></head><body>"; }
    private static String foot() { return "</body></html>"; }
    private static String escape(String value) { return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); }
}
