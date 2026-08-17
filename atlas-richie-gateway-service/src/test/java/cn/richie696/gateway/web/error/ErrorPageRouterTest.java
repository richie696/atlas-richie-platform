package cn.richie696.gateway.web.error;

import cn.richie696.contract.constant.GlobalConstants;
import cn.richie696.component.i18n.config.I18nProperties;
import cn.richie696.gateway.error.GatewayErrorRegistry;
import cn.richie696.gateway.error.codes.GatewayErrorCode;
import cn.richie696.gateway.filter.common.infrastructure.RequestIdGlobalFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.MessageSource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.http.codec.HttpMessageWriter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ErrorPageRouter} Wave 2 契约测试。
 * <p>
 * 覆盖以下契约：
 * </p>
 * <ul>
 *   <li>{@link ApplicationReadyEvent} 触发启动期预渲染（locale 列表 + 详情页缓存）。</li>
 *   <li>{@code GET /gateway/errors} 返回 HTML 列表页（含全部 12 个错误码）。</li>
 *   <li>{@code GET /gateway/errors/{code}} 返回 HTML 详情页（含错误码、HTTP 状态、含义）。</li>
 *   <li>{@code GET /gateway/errors.json} 返回 JSON 清单（含全部 12 个错误码）。</li>
 *   <li>{@code X-RD-Request-Language} 决定 locale；未识别 locale fallback 到默认 locale。</li>
 *   <li>未知错误码返回 404 + NotFound HTML。</li>
 *   <li>所有响应携带 {@code X-Request-Id} 响应头。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-08-04
 */
class ErrorPageRouterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MessageSource messageSource;
    private I18nProperties i18nProperties;
    private ErrorPageRouter router;

    @BeforeEach
    void setUp() {
        messageSource = mock(MessageSource.class);
        i18nProperties = mock(I18nProperties.class);

        when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        when(i18nProperties.getDefaultLocale()).thenReturn(Locale.CHINA);

        router = new ErrorPageRouter(messageSource, i18nProperties);
    }

    @Test
    @DisplayName("ApplicationReadyEvent 触发预渲染，缓存全部 12 个错误码")
    void testApplicationReady_PreRendersAllEntries() {
        router.onApplicationEvent(mock(ApplicationReadyEvent.class));

        ServerResponse index = invokeRouter("/gateway/errors", null);
        assertEquals(200, index.statusCode().value());
        MediaType contentType = index.headers().getContentType();
        assertNotNull(contentType);
        assertTrue(contentType.toString().contains("text/html"));

        String body = writeAndReadBody(index);
        assertTrue(body.contains("GW-AUTH-0001"), "列表页必须包含 GW-AUTH-0001");
        assertTrue(body.contains("GW-SYSTEM-0001"), "列表页必须包含 GW-SYSTEM-0001");
        assertTrue(body.contains("GW-ROUTE-0001"), "列表页必须包含 GW-ROUTE-0001");

        long codeCount = GatewayErrorCode.values().length;
        long linkCount = body.split("href=\"/gateway/errors/GW-", -1).length - 1;
        assertTrue(linkCount >= codeCount,
                "列表页中错误码链接数应 >= " + codeCount + "，实际：" + linkCount);
    }

    @Test
    @DisplayName("GET /gateway/errors.json 返回 JSON 清单，含全部 12 个错误码")
    void testJson_ContainsAllCodes() throws Exception {
        router.onApplicationEvent(mock(ApplicationReadyEvent.class));

        ServerResponse response = invokeRouter("/gateway/errors.json", null);
        assertEquals(200, response.statusCode().value());

        String body = writeAndReadBody(response);
        JsonNode root = MAPPER.readTree(body);

        assertTrue(root.has("codes"), "JSON 清单必须包含 codes 数组");
        JsonNode codes = root.get("codes");
        assertEquals(GatewayErrorCode.values().length, codes.size(),
                "JSON 清单 codes 数组长度必须等于错误码数量");

        for (JsonNode node : codes) {
            assertTrue(node.has("code"));
            assertTrue(node.has("httpStatus"));
            assertTrue(node.has("retryable"));
            assertTrue(node.has("docSlug"));
            assertTrue(node.has("helpUrl"));
            String helpUrl = node.get("helpUrl").asText();
            assertTrue(helpUrl.startsWith(GatewayErrorRegistry.HELP_URL_PREFIX),
                    "helpUrl 必须以 " + GatewayErrorRegistry.HELP_URL_PREFIX + " 开头");
        }
    }

    @Test
    @DisplayName("GET /gateway/errors/GW-AUTH-0001 返回 200 + HTML 详情页")
    void testDetail_KnownCode() {
        router.onApplicationEvent(mock(ApplicationReadyEvent.class));

        ServerResponse response = invokeRouter("/gateway/errors/GW-AUTH-0001", null);
        assertEquals(200, response.statusCode().value());

        String body = writeAndReadBody(response);
        assertTrue(body.contains("GW-AUTH-0001"), "详情页必须包含错误码字符串");
        assertTrue(body.contains("401"), "详情页必须包含 HTTP 状态 401");
        assertTrue(body.contains("/gateway/errors"), "详情页必须包含返回列表页链接");
    }

    @Test
    @DisplayName("GET /gateway/errors/GW-INVALID-9999 返回 404 + NotFound 提示页")
    void testDetail_UnknownCodeReturns404() {
        router.onApplicationEvent(mock(ApplicationReadyEvent.class));

        ServerResponse response = invokeRouter("/gateway/errors/GW-INVALID-9999", null);
        assertEquals(404, response.statusCode().value());

        String body = writeAndReadBody(response);
        assertTrue(body.contains("GW-INVALID-9999"), "404 页应回显查询的错误码");
    }

    @Test
    @DisplayName("错误码大小写不敏感（路径小写也匹配）")
    void testDetail_CaseInsensitive() {
        router.onApplicationEvent(mock(ApplicationReadyEvent.class));

        ServerResponse response = invokeRouter("/gateway/errors/gw-auth-0001", null);
        assertEquals(200, response.statusCode().value(),
                "小写错误码路径也应命中（router 内部 toUpperCase）");
    }

    @Test
    @DisplayName("所有响应携带 X-Request-Id 响应头")
    void testAllResponses_CarryXRequestId() {
        router.onApplicationEvent(mock(ApplicationReadyEvent.class));

        ServerResponse index = invokeRouter("/gateway/errors", null);
        ServerResponse detail = invokeRouter("/gateway/errors/GW-AUTH-0001", null);
        ServerResponse json = invokeRouter("/gateway/errors.json", null);

        assertNotNull(index.headers().getFirst(RequestIdGlobalFilter.HEADER_NAME),
                "列表页响应必须携带 X-Request-Id");
        assertNotNull(detail.headers().getFirst(RequestIdGlobalFilter.HEADER_NAME),
                "详情页响应必须携带 X-Request-Id");
        assertNotNull(json.headers().getFirst(RequestIdGlobalFilter.HEADER_NAME),
                "JSON 清单响应必须携带 X-Request-Id");
    }

    @Test
    @DisplayName("X-RD-Request-Language 头决定 locale（已知 locale）")
    void testLanguageHeader_SelectsLocale() {
        router.onApplicationEvent(mock(ApplicationReadyEvent.class));

        ServerResponse response = invokeRouter("/gateway/errors", "en-US");
        assertEquals(200, response.statusCode().value());
        String body = writeAndReadBody(response);
        assertTrue(body.contains("GW-AUTH-0001"));
    }

    @Test
    @DisplayName("未知 locale fallback 到默认 locale")
    void testLanguageHeader_FallbackToDefault() {
        router.onApplicationEvent(mock(ApplicationReadyEvent.class));

        ServerResponse response = invokeRouter("/gateway/errors", "xx-INVALID");
        assertEquals(200, response.statusCode().value());
    }

    @Test
    @DisplayName("RouterFunction Bean 非 null 且可正常调用")
    void testRouterFunction_NotNull() {
        RouterFunction<ServerResponse> routerFunction = router.errorPageRouterFunction();
        assertNotNull(routerFunction);
    }

    @Test
    @DisplayName("ApplicationReadyEvent 异常时降级到默认 locale（不抛异常）")
    void testApplicationReady_GracefulFailure() {
        when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenThrow(new RuntimeException("boom"));

        router.onApplicationEvent(mock(ApplicationReadyEvent.class));

        ServerResponse response = invokeRouter("/gateway/errors", null);
        assertEquals(200, response.statusCode().value());
    }

    @Test
    @DisplayName("?lang=ja_JP 查询参数优先级高于 X-RD-Request-Language 请求头")
    void testQueryParam_OverridesLanguageHeader() {
        router.onApplicationEvent(mock(ApplicationReadyEvent.class));

        ServerResponse response = invokeRouter(
                "/gateway/errors/GW-AUTH-0001", "en-US", "ja_JP");
        assertEquals(200, response.statusCode().value());

        String body = writeAndReadBody(response);
        assertTrue(body.contains("<html lang=\"ja-JP\""),
                "?lang=ja_JP 必须覆盖请求头, 实际 html lang=" + extractHtmlLang(body));
    }

    @Test
    @DisplayName("?lang= 非法 locale fallback 到请求头 / 默认 locale")
    void testQueryParam_InvalidFallsBackToHeader() {
        router.onApplicationEvent(mock(ApplicationReadyEvent.class));

        ServerResponse response = invokeRouter(
                "/gateway/errors/GW-AUTH-0001", "en-US", "xx-INVALID");
        assertEquals(200, response.statusCode().value());

        String body = writeAndReadBody(response);
        assertTrue(body.contains("<html lang=\"en-US\""),
                "非法 lang 参数应 fallback 到请求头 en-US, 实际 html lang=" + extractHtmlLang(body));
    }

    @Test
    @DisplayName("?lang= 非法 locale + 非法请求头 fallback 到默认 locale")
    void testQueryParam_InvalidBothFallsBackToDefault() {
        router.onApplicationEvent(mock(ApplicationReadyEvent.class));

        ServerResponse response = invokeRouter(
                "/gateway/errors/GW-AUTH-0001", "xx-INVALID", "yy-INVALID");
        assertEquals(200, response.statusCode().value());

        String body = writeAndReadBody(response);
        assertTrue(body.contains("<html lang=\"zh-CN\""),
                "非法 lang + header 应 fallback 到默认 zh-CN, 实际 html lang=" + extractHtmlLang(body));
    }

    @Test
    @DisplayName("详情页和索引页链接保留 ?lang= 参数")
    void testLinks_PreserveLangQueryParam() {
        router.onApplicationEvent(mock(ApplicationReadyEvent.class));

        ServerResponse index = invokeRouter("/gateway/errors", null, "ja_JP");
        String indexBody = writeAndReadBody(index);
        assertTrue(indexBody.contains("?lang=ja-JP"),
                "索引页应包含 ?lang=ja-JP 链接, 实际=" + indexBody.substring(0, Math.min(2000, indexBody.length())));

        ServerResponse detail = invokeRouter("/gateway/errors/GW-AUTH-0003", null, "ja_JP");
        String detailBody = writeAndReadBody(detail);
        assertTrue(detailBody.contains("?lang=ja-JP"),
                "详情页应包含 ?lang=ja-JP 链接");
    }

    @Test
    @DisplayName("列表页包含 brand 顶栏 + 错误码 badge + 状态 pill")
    void testIndex_ContainsBrandAndPills() {
        router.onApplicationEvent(mock(ApplicationReadyEvent.class));

        ServerResponse response = invokeRouter("/gateway/errors", null);
        String body = writeAndReadBody(response);

        assertTrue(body.contains("brand-mark"), "列表页必须包含 brand-mark 顶栏");
        assertTrue(body.contains("error-table"), "列表页必须包含 error-table 样式");
        assertTrue(body.contains("status-pill"), "列表页必须包含 status-pill 状态徽章");
        assertTrue(body.contains("code-badge code-AUTH"),
                "列表页应使用 code-AUTH 命名空间样式");
        assertTrue(body.contains("code-badge code-UPSTREAM"),
                "列表页应使用 code-UPSTREAM 命名空间样式");
        assertTrue(!body.contains("tip-card"), "列表页不应包含 tip-card 提示卡片");
    }

    @Test
    @DisplayName("详情页包含 status pill (5xx 红色) + code badge + breadcrumb")
    void testDetail_ContainsStatusPillAndBreadcrumb() {
        router.onApplicationEvent(mock(ApplicationReadyEvent.class));

        ServerResponse response = invokeRouter("/gateway/errors/GW-SYSTEM-0001", null);
        String body = writeAndReadBody(response);

        assertTrue(body.contains("status-5xx"), "500 系列应使用 status-5xx 样式");
        assertTrue(body.contains("status-pill"), "详情页必须包含 status-pill");
        assertTrue(body.contains("detail-card"), "详情页必须使用 detail-card 卡片样式");
        assertTrue(body.contains("detail-meta"), "详情页必须包含 detail-meta 定义列表");
        assertTrue(body.contains("class=\"breadcrumb\""), "详情页必须包含 breadcrumb 导航");
        assertTrue(body.contains(">500<"), "详情页必须显示 HTTP 状态数字 500");
    }

    @Test
    @DisplayName("4xx 错误码状态 pill 使用 status-4xx（黄色）")
    void testStatusPill_4xxUsesAmber() {
        router.onApplicationEvent(mock(ApplicationReadyEvent.class));

        ServerResponse response = invokeRouter("/gateway/errors/GW-AUTH-0001", null);
        String body = writeAndReadBody(response);

        assertTrue(body.contains("status-4xx"),
                "401 应使用 status-4xx 样式, 实际 body 长度=" + body.length());
    }

    @Test
    @DisplayName("5xx 错误码状态 pill 使用 status-5xx（红色）")
    void testStatusPill_5xxUsesRed() {
        router.onApplicationEvent(mock(ApplicationReadyEvent.class));

        ServerResponse response = invokeRouter("/gateway/errors/GW-SYSTEM-0001", null);
        String body = writeAndReadBody(response);

        assertTrue(body.contains("status-5xx"),
                "500 应使用 status-5xx 样式");
    }

    @Test
    @DisplayName("retry 标记使用 retry-yes / retry-no 样式")
    void testRetryMarker_StyledByRetryable() {
        router.onApplicationEvent(mock(ApplicationReadyEvent.class));

        ServerResponse retryable = invokeRouter("/gateway/errors/GW-RATE-0001", null);
        String retryableBody = writeAndReadBody(retryable);
        assertTrue(retryableBody.contains("retry-yes"),
                "可重试错误码应使用 retry-yes 样式");

        ServerResponse nonRetryable = invokeRouter("/gateway/errors/GW-AUTH-0001", null);
        String nonRetryableBody = writeAndReadBody(nonRetryable);
        assertTrue(nonRetryableBody.contains("retry-no"),
                "不可重试错误码应使用 retry-no 样式");
    }

    @Test
    @DisplayName("语言下拉框使用 SupportedLocale.nativeLabel 作为 option text")
    void testLanguageSwitcher_UsesNativeLabel() {
        router.onApplicationEvent(mock(ApplicationReadyEvent.class));

        ServerResponse response = invokeRouter("/gateway/errors", null);
        String body = writeAndReadBody(response);

        assertTrue(body.contains("简体中文"), "下拉框应包含 nativeLabel=简体中文");
        assertTrue(body.contains("English"), "下拉框应包含 nativeLabel=English");
        assertTrue(body.contains("日本語"), "下拉框应包含 nativeLabel=日本語");
    }

    @Test
    @DisplayName("下拉框 35 个 option 全部出现")
    void testLanguageSwitcher_Has35Options() {
        router.onApplicationEvent(mock(ApplicationReadyEvent.class));

        ServerResponse response = invokeRouter("/gateway/errors", null);
        String body = writeAndReadBody(response);

        long optionCount = body.split("<option ", -1).length - 1;
        assertEquals(SupportedLocale.values().length, optionCount,
                "下拉框 option 数量必须等于 SupportedLocale.values().length, 实际=" + optionCount);
    }

    @Test
    @DisplayName("当前 locale 的 option 被标记为 selected")
    void testLanguageSwitcher_MarksCurrentSelected() {
        router.onApplicationEvent(mock(ApplicationReadyEvent.class));

        ServerResponse response = invokeRouter("/gateway/errors", "en-US");
        String body = writeAndReadBody(response);

        assertTrue(body.contains("value=\"en-US\" selected"),
                "当前 locale en-US 的 option 应标记 selected");
    }

    @Test
    @DisplayName("onchange 切换跳转到 ?lang=xx-XX")
    void testLanguageSwitcher_OnChangeGoesToQueryParam() {
        router.onApplicationEvent(mock(ApplicationReadyEvent.class));

        ServerResponse response = invokeRouter("/gateway/errors", null);
        String body = writeAndReadBody(response);

        assertTrue(body.contains("onchange=\"window.location.search='?lang='+this.value\""),
                "onchange 应跳转到 ?lang=xx-XX");
    }

    @Test
    @DisplayName("RTL locale (ar-SA) 渲染时 html dir=rtl")
    void testRtl_ArabicUsesRtlDirection() {
        router.onApplicationEvent(mock(ApplicationReadyEvent.class));

        ServerResponse response = invokeRouter("/gateway/errors", "ar-SA");
        String body = writeAndReadBody(response);

        assertTrue(body.contains("<html lang=\"ar-SA\" dir=\"rtl\""),
                "ar-SA 应使用 dir=\"rtl\", 实际 html tag="
                        + extractHtmlLang(body));
    }

    @Test
    @DisplayName("LTR locale (en-US) 渲染时 html dir=ltr")
    void testLtr_EnglishUsesLtrDirection() {
        router.onApplicationEvent(mock(ApplicationReadyEvent.class));

        ServerResponse response = invokeRouter("/gateway/errors", "en-US");
        String body = writeAndReadBody(response);

        assertTrue(body.contains("<html lang=\"en-US\" dir=\"ltr\""),
                "en-US 应使用 dir=\"ltr\"");
    }

    @Test
    @DisplayName("SupportedLocale.nativeLabel 关键值正确")
    void testSupportedLocale_NativeLabels() {
        assertEquals("简体中文", SupportedLocale.ZH_CN.getNativeLabel());
        assertEquals("繁體中文", SupportedLocale.ZH_TW.getNativeLabel());
        assertEquals("English", SupportedLocale.EN_US.getNativeLabel());
        assertEquals("日本語", SupportedLocale.JA_JP.getNativeLabel());
        assertEquals("Español (México)", SupportedLocale.ES_MX.getNativeLabel());
        assertEquals("العربية", SupportedLocale.AR_SA.getNativeLabel());
    }

    private static String extractHtmlLang(String body) {
        int idx = body.indexOf("<html lang=\"");
        if (idx < 0) {
            return "<not-found>";
        }
        int end = body.indexOf('"', idx + "<html lang=\"".length());
        return end < 0 ? "<not-found>" : body.substring(idx, end + 1);
    }

    private ServerResponse invokeRouter(String path, String language) {
        return invokeRouter(path, language, null);
    }

    private ServerResponse invokeRouter(String path, String language, String langQueryParam) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get(path);
        if (language != null) {
            builder.header(GlobalConstants.X_RD_REQUEST_LANGUAGE, language);
        }
        if (langQueryParam != null) {
            builder.queryParam("lang", langQueryParam);
        }
        ServerWebExchange exchange = MockServerWebExchange.from(builder.build());
        ServerRequest serverRequest = ServerRequest.create(exchange, Collections.emptyList());

        RouterFunction<ServerResponse> routerFunction = router.errorPageRouterFunction();
        Mono<ServerResponse> mono = routerFunction.route(serverRequest)
                .flatMap(handlerFunction -> handlerFunction.handle(serverRequest));
        ServerResponse response = mono.block();
        assertNotNull(response, "RouterFunction 必须产生响应");
        return response;
    }

    private String writeAndReadBody(ServerResponse response) {
        MockServerHttpRequest request = MockServerHttpRequest.get("/dummy").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        response.writeTo(exchange, createServerResponseContext()).block();
        MockServerHttpResponse mockResponse = (MockServerHttpResponse) exchange.getResponse();
        return readBodyAsString(mockResponse);
    }

    private static ServerResponse.Context createServerResponseContext() {
        List<HttpMessageWriter<?>> writers = ExchangeStrategies.withDefaults().messageWriters();
        return new ServerResponse.Context() {
            @Override
            public List<HttpMessageWriter<?>> messageWriters() {
                return writers;
            }

            @Override
            public List<ViewResolver> viewResolvers() {
                return Collections.emptyList();
            }
        };
    }

    private static String readBodyAsString(MockServerHttpResponse response) {
        Flux<DataBuffer> body = response.getBody();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (DataBuffer buffer : body.toIterable()) {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            baos.writeBytes(bytes);
        }
        try {
            return baos.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unused")
    private static Optional<ServerWebExchange> dummyExchange() {
        return Optional.empty();
    }
}

