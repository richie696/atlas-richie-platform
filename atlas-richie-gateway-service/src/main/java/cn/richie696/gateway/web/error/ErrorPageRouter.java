package cn.richie696.gateway.web.error;

import cn.richie696.component.i18n.config.I18nProperties;
import cn.richie696.gateway.error.GatewayErrorEntry;
import cn.richie696.gateway.error.GatewayErrorRegistry;
import cn.richie696.gateway.filter.common.infrastructure.RequestIdGlobalFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 错误码文档 RouterFunction + 启动期多 locale HTML 预渲染器（Plan C）。
 * <p>
 * 暴露 3 个路由：
 * </p>
 * <ul>
 *   <li>{@code GET /gateway/errors}：列表页 HTML（按 X-RD-Request-Language 选择 locale）</li>
 *   <li>{@code GET /gateway/errors/{code}}：详情页 HTML（按 X-RD-Request-Language 选择 locale）</li>
 *   <li>{@code GET /gateway/errors.json}：JSON 清单（locale 不影响）</li>
 * </ul>
 *
 * <h2>启动期预渲染</h2>
 * <p>
 * 通过 {@link ApplicationListener} 监听 {@link ApplicationReadyEvent}，
 * 遍历 {@code classpath:i18n/messages*.properties} 收集所有可用 locale，
 * 对每个 locale × 12 个 {@link GatewayErrorEntry} 预渲染 HTML 字符串并缓存；
 * 同时预渲染列表页。运行期请求零渲染开销。
 * </p>
 *
 * <h2>locale 解析</h2>
 * <ol>
 *   <li>优先取请求头 {@code X-RD-Request-Language}。</li>
 *   <li>校验该 locale 是否在启动期收集的可用列表内；不在则 fallback。</li>
 *   <li>fallback 到 {@link I18nProperties#getDefaultLocale()}（默认 {@code zh_CN}）。</li>
 * </ol>
 *
 * @author richie696
 * @since 2026-08-04
 */
@Slf4j
@Component
public class ErrorPageRouter implements ApplicationListener<ApplicationReadyEvent> {

    /**
     * 列表页路径。
     */
    public static final String PATH_INDEX = "/gateway/errors";

    /**
     * JSON 清单路径。
     */
    public static final String PATH_JSON = "/gateway/errors.json";

    /**
     * 详情页路径模板（{@code {code}} 占位）。
     */
    public static final String PATH_DETAIL = "/gateway/errors/{code}";

    /**
     * i18n 资源目录 ant 模式。
     */
    private static final String I18N_PATTERN = "classpath:i18n/messages*.properties";

    /**
     * i18n key: 列表页标题。
     */
    private static final String KEY_INDEX_TITLE = "gateway.error.index.title";

    /**
     * i18n key: 列表页副标题。
     */
    private static final String KEY_INDEX_SUBTITLE = "gateway.error.index.subtitle";

    /**
     * i18n key: breadcrumb 首页。
     */
    private static final String KEY_CRUMB_HOME = "gateway.error.crumb.home";

    /**
     * i18n key: 详情页 retry=true 文案。
     */
    private static final String KEY_RETRY_YES = "gateway.error.retry.yes";

    /**
     * i18n key: 详情页 retry=false 文案。
     */
    private static final String KEY_RETRY_NO = "gateway.error.retry.no";

    /**
     * i18n key: 详情页 HTTP 状态前缀（如 "HTTP 401"）。
     */
    private static final String KEY_HTTP_PREFIX = "gateway.error.httpPrefix";

    /**
     * i18n key: 列头。
     */
    private static final String KEY_COL_CODE = "gateway.error.col.code";
    private static final String KEY_COL_MEANING = "gateway.error.col.meaning";
    private static final String KEY_COL_CAUSE = "gateway.error.col.cause";
    private static final String KEY_COL_INVESTIGATE = "gateway.error.col.investigate";
    private static final String KEY_COL_RETRY = "gateway.error.col.retry";
    private static final String KEY_COL_VERSION = "gateway.error.col.version";

    /**
     * i18n key: 详情页标签。
     */
    private static final String KEY_LABEL_CODE = "gateway.error.label.code";
    private static final String KEY_LABEL_HTTP_STATUS = "gateway.error.label.httpStatus";
    private static final String KEY_LABEL_MEANING = "gateway.error.label.meaning";
    private static final String KEY_LABEL_CAUSE = "gateway.error.label.cause";
    private static final String KEY_LABEL_INVESTIGATE = "gateway.error.label.investigate";
    private static final String KEY_LABEL_RETRY = "gateway.error.label.retry";
    private static final String KEY_LABEL_VERSION = "gateway.error.label.version";
    private static final String KEY_LABEL_HELP_URL = "gateway.error.label.helpUrl";
    private static final String KEY_BACK = "gateway.error.back";
    private static final String KEY_NOT_FOUND_TITLE = "gateway.error.notFound.title";
    private static final String KEY_NOT_FOUND_HINT = "gateway.error.notFound.hint";

    /**
     * Spring MessageSource。
     */
    private final MessageSource messageSource;

    /**
     * i18n 配置（默认 locale 等）。
     */
    private final I18nProperties i18nProperties;

    /**
     * Jackson 用于 JSON 清单序列化。
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 可用 locale 列表（启动期填充）。
     */
    private List<Locale> availableLocales = Collections.emptyList();

    /**
     * 可用 locale 的语言标签集合（用于 {@link SupportedLocale} 反查）。
     */
    private java.util.Set<String> availableLocaleTags = Collections.emptySet();

    /**
     * 默认 locale（启动期填充）。
     */
    private Locale defaultLocale = Locale.CHINA;

    /**
     * 列表页 HTML 缓存：localeTag → HTML。
     */
    private Map<String, String> indexHtmlByLocale = Collections.emptyMap();

    /**
     * 详情页 HTML 缓存：localeTag → (code → HTML)。
     */
    private Map<String, Map<String, String>> detailHtmlByLocale = Collections.emptyMap();

    /**
     * 构造方法。
     *
     * @param messageSource Spring MessageSource
     * @param i18nProperties i18n 配置
     */
    public ErrorPageRouter(MessageSource messageSource, I18nProperties i18nProperties) {
        this.messageSource = messageSource;
        this.i18nProperties = i18nProperties;
    }

    @Override
    public void onApplicationEvent(@Nonnull ApplicationReadyEvent event) {
        long start = System.currentTimeMillis();
        try {
            initLocaleAndCache();
        } catch (Exception ex) {
            log.error("ErrorPageRouter 启动期预渲染失败", ex);
            // 降级：仅保留默认 locale 的渲染
            try {
                defaultLocale = i18nProperties.getDefaultLocale();
                if (defaultLocale == null) {
                    defaultLocale = Locale.CHINA;
                }
                availableLocales = List.of(defaultLocale);
                indexHtmlByLocale = Map.of(defaultLocale.toLanguageTag(), renderIndexHtml(defaultLocale));
                Map<String, String> fallbackDetail = new LinkedHashMap<>();
                for (GatewayErrorEntry entry : GatewayErrorRegistry.getAll()) {
                    fallbackDetail.put(entry.getErrorCode(), renderDetailHtml(defaultLocale, entry));
                }
                detailHtmlByLocale = Map.of(defaultLocale.toLanguageTag(), fallbackDetail);
            } catch (Exception innerEx) {
                log.error("ErrorPageRouter 降级渲染也失败", innerEx);
            }
            return;
        }
        log.info("ErrorPageRouter 启动期预渲染完成: {} locales, {} 详情页, 耗时 {} ms",
                availableLocales.size(),
                detailHtmlByLocale.values().stream().mapToInt(Map::size).sum(),
                System.currentTimeMillis() - start);
    }

    /**
     * 初始化可用 locale 列表与 HTML 缓存。
     *
     * @throws IOException 资源读取异常
     */
    private void initLocaleAndCache() throws IOException {
        defaultLocale = i18nProperties.getDefaultLocale();
        if (defaultLocale == null) {
            defaultLocale = Locale.CHINA;
        }
        List<Locale> locales = collectLocales();
        if (!locales.contains(defaultLocale)) {
            locales.add(defaultLocale);
        }
        availableLocales = Collections.unmodifiableList(locales);
        availableLocaleTags = locales.stream()
                .map(Locale::toLanguageTag)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        Map<String, String> indexMap = new LinkedHashMap<>();
        Map<String, Map<String, String>> detailMap = new LinkedHashMap<>();
        for (Locale locale : availableLocales) {
            indexMap.put(locale.toLanguageTag(), renderIndexHtml(locale));
            Map<String, String> perLocale = new LinkedHashMap<>();
            for (GatewayErrorEntry entry : GatewayErrorRegistry.getAll()) {
                perLocale.put(entry.getErrorCode(), renderDetailHtml(locale, entry));
            }
            detailMap.put(locale.toLanguageTag(), Collections.unmodifiableMap(perLocale));
        }
        indexHtmlByLocale = Collections.unmodifiableMap(indexMap);
        detailHtmlByLocale = Collections.unmodifiableMap(detailMap);
    }

    /**
     * 收集 {@code i18n/messages*.properties} 中所有 locale。
     *
     * @return locale 列表（去重）
     * @throws IOException 资源读取异常
     */
    private List<Locale> collectLocales() throws IOException {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(I18N_PATTERN);
        List<Locale> result = new ArrayList<>();
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null || !filename.endsWith(".properties")) {
                continue;
            }
            String localeStr = extractLocaleTag(filename);
            if (localeStr.isEmpty()) {
                continue;
            }
            Locale locale = Locale.forLanguageTag(localeStr);
            if (!result.contains(locale)) {
                result.add(locale);
            }
        }
        return result;
    }

    /**
     * 从文件名（如 {@code messages_zh_CN.properties}）提取 locale tag（如 {@code zh-CN}）。
     *
     * @param filename 资源文件名
     * @return locale tag；若文件名为默认 {@code messages.properties} 返回空串
     */
    private static String extractLocaleTag(String filename) {
        if ("messages.properties".equals(filename)) {
            return "";
        }
        String stem = filename.substring("messages_".length());
        if (stem.endsWith(".properties")) {
            stem = stem.substring(0, stem.length() - ".properties".length());
        }
        return stem.replace('_', '-');
    }

    /**
     * 渲染列表页 HTML。
     *
     * @param locale 目标 locale
     * @return HTML 字符串
     */
    private String renderIndexHtml(Locale locale) {
        String title = resolve(KEY_INDEX_TITLE, locale);
        String subtitle = resolve(KEY_INDEX_SUBTITLE, locale);
        String cCode = resolve(KEY_COL_CODE, locale);
        String cMeaning = resolve(KEY_COL_MEANING, locale);
        String cCause = resolve(KEY_COL_CAUSE, locale);
        String cInvestigate = resolve(KEY_COL_INVESTIGATE, locale);
        String cRetry = resolve(KEY_COL_RETRY, locale);
        String cVersion = resolve(KEY_COL_VERSION, locale);
        String back = resolve(KEY_BACK, locale);
        String crumbHome = resolve(KEY_CRUMB_HOME, locale);

        StringBuilder rows = new StringBuilder();
        for (GatewayErrorEntry entry : GatewayErrorRegistry.getAll()) {
            String link = localeAwareLink(entry.getHelpUrl(), locale);
            rows.append("<tr>")
                    .append("<td class=\"col-code\"><a class=\"code-badge ")
                    .append(codeBadgeClass(entry.getErrorCode()))
                    .append("\" href=\"").append(link).append("\">")
                    .append(escape(entry.getErrorCode())).append("</a></td>")
                    .append("<td class=\"col-status\">")
                    .append(renderStatusPill(entry.getHttpStatus())).append("</td>")
                    .append("<td>").append(escape(resolve(entry.getI18nKey() + ".meaning", locale))).append("</td>")
                    .append("<td>").append(escape(resolve(entry.getI18nKey() + ".cause", locale))).append("</td>")
                    .append("<td class=\"col-investigate\">")
                    .append(escape(resolve(entry.getI18nKey() + ".investigate", locale))).append("</td>")
                    .append("<td class=\"col-retry\">")
                    .append(renderRetry(entry.isRetryable(), locale)).append("</td>")
                    .append("<td class=\"col-version\">")
                    .append("<span class=\"version\">").append(escape(entry.getVersion())).append("</span></td>")
                    .append("</tr>");
        }

        String switcher = renderLanguageSwitcher(locale);
        String langTag = locale.toLanguageTag();

        return renderHead(title, langTag, switcher)
                + "  <main class=\"container\">\n"
                + "    " + renderBreadcrumb(crumbHome, null, localeAwareLink(PATH_INDEX, locale)) + "\n"
                + "    <div class=\"page-header\">\n"
                + "      <h1>" + escape(title) + "</h1>\n"
                + "      <p class=\"subtitle\">" + escape(subtitle) + "</p>\n"
                + "    </div>\n"
                + "    <table class=\"error-table\">\n"
                + "      <thead><tr>"
                + "<th class=\"col-code\">" + escape(cCode) + "</th>"
                + "<th class=\"col-status\">HTTP</th>"
                + "<th>" + escape(cMeaning) + "</th>"
                + "<th>" + escape(cCause) + "</th>"
                + "<th class=\"col-investigate\">" + escape(cInvestigate) + "</th>"
                + "<th class=\"col-retry\">" + escape(cRetry) + "</th>"
                + "<th class=\"col-version\">" + escape(cVersion) + "</th>"
                + "</tr></thead>\n"
                + "      <tbody>"
                + rows
                + "</tbody>\n"
                + "    </table>\n"
                + "    <a class=\"back-link\" href=\"" + localeAwareLink(PATH_INDEX, locale) + "\">← "
                + escape(back) + "</a>\n"
                + "  </main>\n"
                + "</body>\n"
                + "</html>";
    }

    /**
     * 渲染详情页 HTML。
     *
     * @param locale 目标 locale
     * @param entry  错误码条目
     * @return HTML 字符串
     */
    private String renderDetailHtml(Locale locale, GatewayErrorEntry entry) {
        String meaning = resolve(entry.getI18nKey() + ".meaning", locale);
        String title = entry.getErrorCode() + " · " + meaning;
        String lCode = resolve(KEY_LABEL_CODE, locale);
        String lHttpStatus = resolve(KEY_LABEL_HTTP_STATUS, locale);
        String lMeaning = resolve(KEY_LABEL_MEANING, locale);
        String lCause = resolve(KEY_LABEL_CAUSE, locale);
        String lInvestigate = resolve(KEY_LABEL_INVESTIGATE, locale);
        String lRetry = resolve(KEY_LABEL_RETRY, locale);
        String lVersion = resolve(KEY_LABEL_VERSION, locale);
        String lHelpUrl = resolve(KEY_LABEL_HELP_URL, locale);
        String back = resolve(KEY_BACK, locale);
        String crumbHome = resolve(KEY_CRUMB_HOME, locale);
        String httpPrefix = resolve(KEY_HTTP_PREFIX, locale);

        String cause = resolve(entry.getI18nKey() + ".cause", locale);
        String investigate = resolve(entry.getI18nKey() + ".investigate", locale);

        String switcher = renderLanguageSwitcher(locale);
        String langTag = locale.toLanguageTag();

        return renderHead(title, langTag, switcher)
                + "  <main class=\"container\">\n"
                + "    " + renderBreadcrumb(crumbHome, entry.getErrorCode(),
                        localeAwareLink(PATH_INDEX, locale)) + "\n"
                + "    <div class=\"detail-card\">\n"
                + "      <div class=\"detail-header\">\n"
                + "        <span class=\"code-badge " + codeBadgeClass(entry.getErrorCode()) + "\">"
                + escape(entry.getErrorCode()) + "</span>\n"
                + "        <span class=\"status-pill "
                + (entry.getHttpStatus() >= 500 ? "status-5xx" : "status-4xx") + "\">"
                + escape(httpPrefix) + " " + entry.getHttpStatus() + "</span>\n"
                + "      </div>\n"
                + "      <h1 class=\"detail-title\">" + escape(meaning) + "</h1>\n"
                + "      <dl class=\"detail-meta\">\n"
                + "        <div class=\"row\"><dt>" + escape(lCode) + "</dt><dd>"
                + escape(entry.getErrorCode()) + "</dd></div>\n"
                + "        <div class=\"row\"><dt>" + escape(lHttpStatus) + "</dt><dd>"
                + renderStatusPill(entry.getHttpStatus()) + "</dd></div>\n"
                + "        <div class=\"row\"><dt>" + escape(lCause) + "</dt><dd>"
                + escape(cause) + "</dd></div>\n"
                + "        <div class=\"row\"><dt>" + escape(lInvestigate) + "</dt><dd>"
                + escape(investigate) + "</dd></div>\n"
                + "        <div class=\"row\"><dt>" + escape(lRetry) + "</dt><dd>"
                + renderRetry(entry.isRetryable(), locale) + "</dd></div>\n"
                + "        <div class=\"row\"><dt>" + escape(lVersion) + "</dt><dd>"
                + "<span class=\"version\">" + escape(entry.getVersion()) + "</span></dd></div>\n"
                + "        <div class=\"row\"><dt>" + escape(lHelpUrl) + "</dt><dd>"
                + "<code>" + escape(entry.getHelpUrl()) + "</code></dd></div>\n"
                + "      </dl>\n"
                + "    </div>\n"
                + "    <a class=\"back-link\" href=\"" + localeAwareLink(PATH_INDEX, locale) + "\">← "
                + escape(back) + "</a>\n"
                + "  </main>\n"
                + "</body>\n"
                + "</html>";
    }

    /**
     * 渲染"错误码不存在"详情页（运行时 fallback）。
     *
     * @param locale 请求 locale
     * @param code   错误码
     * @return HTML 字符串
     */
    private String renderNotFoundHtml(Locale locale, String code) {
        String title = resolve(KEY_NOT_FOUND_TITLE, locale);
        String hint = resolve(KEY_NOT_FOUND_HINT, locale);
        String back = resolve(KEY_BACK, locale);
        String crumbHome = resolve(KEY_CRUMB_HOME, locale);
        String langTag = locale.toLanguageTag();
        String switcher = renderLanguageSwitcher(locale);

        return renderHead(title, langTag, switcher)
                + "  <main class=\"container\">\n"
                + "    " + renderBreadcrumb(crumbHome, code, localeAwareLink(PATH_INDEX, locale)) + "\n"
                + "    <div class=\"detail-card\">\n"
                + "      <h1 class=\"detail-title\">" + escape(title) + "</h1>\n"
                + "      <p class=\"detail-meaning\">" + escape(hint) + "</p>\n"
                + "      <div class=\"not-found\">" + escape(code) + "</div>\n"
                + "    </div>\n"
                + "    <a class=\"back-link\" href=\"" + localeAwareLink(PATH_INDEX, locale) + "\">← "
                + escape(back) + "</a>\n"
                + "  </main>\n"
                + "</body>\n"
                + "</html>";
    }

    /**
     * 解析 i18n 文案（使用指定 locale，不受 LocaleContextHolder 影响）。
     *
     * @param key    i18n key
     * @param locale 目标 locale
     * @return 文案，找不到时返回 key 本身
     */
    private String resolve(String key, Locale locale) {
        try {
            return messageSource.getMessage(key, null, locale);
        } catch (Exception ex) {
            log.debug("i18n key 解析失败: key={}, locale={}", key, locale, ex);
            return key;
        }
    }

    /**
     * HTML 转义（防 XSS + 渲染异常）。
     *
     * @param input 原始字符串
     * @return 转义后字符串
     */
    private static String escape(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 注册 RouterFunction Bean，供 Spring Cloud Gateway 自动发现。
     *
     * @return 错误码文档路由
     */
    @org.springframework.context.annotation.Bean
    public RouterFunction<ServerResponse> errorPageRouterFunction() {
        return RouterFunctions.route(org.springframework.web.reactive.function.server.RequestPredicates.GET(PATH_INDEX), this::handleIndex)
                .andRoute(org.springframework.web.reactive.function.server.RequestPredicates.GET(PATH_JSON), this::handleJson)
                .andRoute(org.springframework.web.reactive.function.server.RequestPredicates.GET(PATH_DETAIL), this::handleDetail);
    }

    private Mono<ServerResponse> handleIndex(ServerRequest request) {
        Locale locale = resolveLocale(request);
        String html = indexHtmlByLocale.getOrDefault(locale.toLanguageTag(),
                indexHtmlByLocale.get(defaultLocale.toLanguageTag()));
        if (html == null) {
            html = renderIndexHtml(locale);
        }
        return ServerResponse.ok()
                .contentType(MediaType.TEXT_HTML)
                .header(RequestIdGlobalFilter.HEADER_NAME, requestIdOrNew(request))
                .bodyValue(html);
    }

    private Mono<ServerResponse> handleJson(ServerRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        List<Map<String, Object>> codes = new ArrayList<>();
        for (GatewayErrorEntry entry : GatewayErrorRegistry.getAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", entry.getErrorCode());
            m.put("httpStatus", entry.getHttpStatus());
            m.put("retryable", entry.isRetryable());
            m.put("docSlug", entry.getDocSlug());
            m.put("i18nKey", entry.getI18nKey());
            m.put("version", entry.getVersion());
            m.put("helpUrl", entry.getHelpUrl());
            codes.add(m);
        }
        body.put("codes", codes);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(RequestIdGlobalFilter.HEADER_NAME, requestIdOrNew(request))
                    .bodyValue(new String(bytes, StandardCharsets.UTF_8));
        } catch (JsonProcessingException ex) {
            log.error("序列化错误码 JSON 清单失败", ex);
            return ServerResponse.status(500).build();
        }
    }

    private Mono<ServerResponse> handleDetail(ServerRequest request) {
        String rawCode = request.pathVariable("code");
        String code = rawCode.toUpperCase();
        GatewayErrorEntry entry = GatewayErrorRegistry.getByCode(code);
        Locale locale = resolveLocale(request);
        if (entry == null) {
            return ServerResponse.status(404)
                    .contentType(MediaType.TEXT_HTML)
                    .header(RequestIdGlobalFilter.HEADER_NAME, requestIdOrNew(request))
                    .bodyValue(renderNotFoundHtml(locale, code));
        }
        Map<String, String> perLocale = detailHtmlByLocale.getOrDefault(locale.toLanguageTag(),
                detailHtmlByLocale.get(defaultLocale.toLanguageTag()));
        String html = perLocale != null ? perLocale.get(code) : null;
        if (html == null) {
            html = renderDetailHtml(locale, entry);
        }
        return ServerResponse.ok()
                .contentType(MediaType.TEXT_HTML)
                .header(RequestIdGlobalFilter.HEADER_NAME, requestIdOrNew(request))
                .bodyValue(html);
    }

    /**
     * 查询参数名：locale 显式覆盖。
     */
    private static final String QUERY_PARAM_LANG = "lang";

    /**
     * 解析请求 locale。优先级：
     * <ol>
     *   <li>查询参数 {@code ?lang=xx_XX}（最高优先级，调用方显式指定）</li>
     *   <li>请求头 {@code X-RD-Request-Language}（自动协商）</li>
     *   <li>{@link I18nProperties#getDefaultLocale()}（默认 locale）</li>
     * </ol>
     *
     * <p>所有来源都会校验该 locale 是否在启动期收集的可用列表内；不在则 fallback 到默认。</p>
     *
     * @param request ServerRequest
     * @return 解析后的 locale（保证在 {@link #availableLocales} 中或为默认）
     */
    private Locale resolveLocale(ServerRequest request) {
        Locale fromQuery = readLangQueryParam(request);
        if (fromQuery != null) {
            LocaleContextHolder.setLocale(fromQuery);
            return fromQuery;
        }
        String headerLang = request.headers().firstHeader(
                cn.richie696.contract.constant.GlobalConstants.X_RD_REQUEST_LANGUAGE);
        if (StringUtils.hasText(headerLang)) {
            Locale candidate = Locale.forLanguageTag(headerLang.replace('_', '-'));
            if (availableLocales.contains(candidate)) {
                LocaleContextHolder.setLocale(candidate);
                return candidate;
            }
        }
        LocaleContextHolder.setLocale(defaultLocale);
        return defaultLocale;
    }

    /**
     * 从查询参数 {@code ?lang=xx_XX} 解析 locale。
     *
     * @param request ServerRequest
     * @return 合法且在可用列表内的 locale；否则返回 {@code null}，由调用方继续 fallback
     */
    private Locale readLangQueryParam(ServerRequest request) {
        String lang = request.queryParam(QUERY_PARAM_LANG).orElse(null);
        if (!StringUtils.hasText(lang)) {
            return null;
        }
        String normalized = lang.replace('_', '-');
        if (SupportedLocale.fromTag(normalized) != null
                && availableLocaleTags.contains(normalized)) {
            return Locale.forLanguageTag(normalized);
        }
        Locale candidate = Locale.forLanguageTag(normalized);
        return availableLocales.contains(candidate) ? candidate : null;
    }

    /**
     * 为页面内链生成保留 {@code ?lang=xx_XX} 的 URL。
     *
     * @param path   目标路径（不带 query）
     * @param locale 当前 locale
     * @return 拼接后的 URL（始终带 {@code ?lang=}，便于服务端确定性渲染）
     */
    private static String localeAwareLink(String path, Locale locale) {
        return path + "?lang=" + locale.toLanguageTag();
    }

    /**
     * 渲染语言切换下拉框 HTML。
     *
     * <p>下拉框包含全部 {@link SupportedLocale}，每个 option 的 {@code value} 为 BCP 47 标签
     * （如 {@code "zh-CN"}），展示文案使用每种语言自己的母语（如 {@code "简体中文"}、
     * {@code "English"}、{@code "日本語"}），由 {@link SupportedLocale#getNativeLabel()} 提供。
     * onchange 触发后跳转到 {@code ?lang=xx-XX} 的同一路径，从而保持列表/详情页状态一致。</p>
     *
     * @param current 当前请求 locale
     * @return 完整 {@code <form>+<select>} HTML 字符串
     */
    private String renderLanguageSwitcher(Locale current) {
        StringBuilder options = new StringBuilder();
        for (SupportedLocale sl : SupportedLocale.values()) {
            String tag = sl.getTag();
            String label = sl.getNativeLabel();
            options.append("<option value=\"")
                    .append(escape(tag))
                    .append('"');
            if (current.toLanguageTag().equalsIgnoreCase(tag)) {
                options.append(" selected");
            }
            options.append('>')
                    .append(escape(label))
                    .append("</option>");
        }
        return "<form class=\"lang-switcher\" method=\"get\" onsubmit=\"return false;\">"
                + "<label for=\"lang-select\">🌐</label>"
                + "<select id=\"lang-select\" onchange=\"window.location.search='?lang='+this.value\">"
                + options
                + "</select></form>";
    }

    /**
     * 取请求的 requestId（无则生成 32 位 hex）。
     *
     * @param request ServerRequest
     * @return requestId
     */
    private String requestIdOrNew(ServerRequest request) {
        String rid = request.headers().firstHeader(RequestIdGlobalFilter.HEADER_NAME);
        if (rid != null && !rid.isEmpty()) {
            return rid;
        }
        byte[] bytes = new byte[16];
        new java.security.SecureRandom().nextBytes(bytes);
        char[] hex = new char[32];
        char[] hexChars = "0123456789abcdef".toCharArray();
        for (int i = 0; i < 16; i++) {
            int v = bytes[i] & 0xff;
            hex[i * 2] = hexChars[v >>> 4];
            hex[i * 2 + 1] = hexChars[v & 0x0f];
        }
        return new String(hex);
    }

    // 样式与 HTML 模板

    /**
     * 共享 CSS（GitHub-inspired 浅色主题：CSS 变量 + 卡片布局 + 状态 pill + 错误码 badge）。
     * 内联而非外置静态文件，原因是 Plan C 预渲染策略不引入任何静态资源依赖。
     */
    private static final String CSS = ""
            + ":root{"
            + "--bg:#f6f8fa;--surface:#ffffff;--surface-alt:#f9fafb;"
            + "--border:#d0d7de;--border-muted:#e6e8eb;"
            + "--text:#1f2328;--text-muted:#656d76;"
            + "--primary:#0969da;--primary-bg:#ddf4ff;--primary-border:#54aeff;"
            + "--success:#1a7f37;--success-bg:#dafbe1;"
            + "--warning:#9a6700;--warning-bg:#fff8c5;--warning-border:#d4a72c;"
            + "--danger:#cf222e;--danger-bg:#ffebe9;--danger-border:#ff8182;"
            + "--shadow:0 1px 0 rgba(31,35,40,.04);"
            + "--shadow-md:0 3px 6px rgba(140,149,159,.15);"
            + "--radius:6px;--radius-lg:8px;"
            + "}"
            + "*{box-sizing:border-box;}"
            + "body{margin:0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','Noto Sans',Helvetica,Arial,sans-serif,'Apple Color Emoji','Segoe UI Emoji';font-size:14px;line-height:1.5;color:var(--text);background:var(--bg);-webkit-font-smoothing:antialiased;}"
            + ".topbar{background:var(--surface);border-bottom:1px solid var(--border);padding:12px 24px;display:flex;align-items:center;justify-content:space-between;position:sticky;top:0;z-index:10;}"
            + ".brand{display:flex;align-items:center;gap:10px;}"
            + ".brand-mark{display:inline-flex;width:28px;height:28px;background:linear-gradient(135deg,#0969da,#1f6feb);color:#fff;border-radius:var(--radius);align-items:center;justify-content:center;font-weight:700;font-size:15px;}"
            + ".brand-name{font-weight:600;font-size:15px;}"
            + ".brand-sub{color:var(--text-muted);font-size:13px;padding-left:10px;border-left:1px solid var(--border);margin-left:4px;}"
            + ".lang-switcher{display:inline-flex;align-items:center;gap:8px;margin:0;}"
            + ".lang-switcher>label{font-size:16px;cursor:default;}"
            + ".lang-switcher select{padding:5px 28px 5px 10px;border-radius:var(--radius);border:1px solid var(--border);background:var(--surface) url(\"data:image/svg+xml;charset=utf-8,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 12 12'%3E%3Cpath fill='%23656d76' d='M3 4.5l3 3 3-3z'/%3E%3C/svg%3E\") no-repeat right 8px center;background-size:12px;font-size:13px;cursor:pointer;appearance:none;-webkit-appearance:none;min-width:160px;color:var(--text);}"
            + ".lang-switcher select:hover{border-color:var(--primary-border);}"
            + ".lang-switcher select:focus{outline:none;border-color:var(--primary);box-shadow:0 0 0 3px rgba(9,105,218,.2);}"
            + ".container{max-width:1200px;margin:0 auto;padding:28px 24px;}"
            + ".breadcrumb{display:flex;align-items:center;gap:6px;font-size:13px;color:var(--text-muted);margin-bottom:14px;flex-wrap:wrap;}"
            + ".breadcrumb a{color:var(--primary);text-decoration:none;}"
            + ".breadcrumb a:hover{text-decoration:underline;}"
            + ".breadcrumb .sep{color:var(--text-muted);opacity:.6;}"
            + ".breadcrumb .current{color:var(--text);font-weight:500;}"
            + ".page-header{margin-bottom:20px;}"
            + ".page-header h1{margin:0;font-size:26px;font-weight:600;letter-spacing:-.5px;}"
            + ".page-header .subtitle{margin:6px 0 0;color:var(--text-muted);font-size:13px;}"
            + ".error-table{width:100%;background:var(--surface);border:1px solid var(--border);border-radius:var(--radius-lg);border-collapse:separate;border-spacing:0;overflow:hidden;box-shadow:var(--shadow);}"
            + ".error-table th,.error-table td{padding:11px 14px;text-align:left;vertical-align:top;border-bottom:1px solid var(--border-muted);font-size:13px;}"
            + ".error-table thead th{background:var(--surface-alt);font-weight:600;font-size:11px;text-transform:uppercase;letter-spacing:.5px;color:var(--text-muted);}"
            + ".error-table tbody tr:hover{background:var(--surface-alt);}"
            + ".error-table tbody tr:last-child td{border-bottom:0;}"
            + ".col-code{min-width:150px;}"
            + ".col-status{min-width:80px;}"
            + ".col-retry{min-width:90px;}"
            + ".col-version{min-width:80px;}"
            + ".code-badge{display:inline-block;padding:2px 8px;border-radius:var(--radius);font-family:ui-monospace,SFMono-Regular,'SF Mono',Menlo,Consolas,monospace;font-size:12px;font-weight:600;text-decoration:none;white-space:nowrap;transition:transform .1s;}"
            + ".code-badge:hover{transform:translateY(-1px);}"
            + ".code-AUTH{background:#ffebe9;color:var(--danger);border:1px solid #ffcecb;}"
            + ".code-TENANT{background:#fff8c5;color:var(--warning);border:1px solid var(--warning-border);}"
            + ".code-RATE{background:#fff1e5;color:#bc4c00;border:1px solid #ffb86c;}"
            + ".code-UPSTREAM{background:#ddf4ff;color:var(--primary);border:1px solid var(--primary-border);}"
            + ".code-SYSTEM{background:#ffebe9;color:var(--danger);border:1px solid var(--danger-border);}"
            + ".code-ROUTE{background:#f5f5f5;color:var(--text);border:1px solid var(--border);}"
            + ".code-REQ{background:#fff1e5;color:#bc4c00;border:1px solid #ffb86c;}"
            + ".status-pill{display:inline-block;padding:2px 9px;border-radius:12px;font-family:ui-monospace,SFMono-Regular,'SF Mono',Menlo,Consolas,monospace;font-size:11px;font-weight:700;min-width:44px;text-align:center;line-height:1.5;}"
            + ".status-2xx{background:var(--success-bg);color:var(--success);border:1px solid #4ac26b;}"
            + ".status-4xx{background:var(--warning-bg);color:var(--warning);border:1px solid var(--warning-border);}"
            + ".status-5xx{background:var(--danger-bg);color:var(--danger);border:1px solid var(--danger-border);}"
            + ".retry-yes{color:var(--success);font-weight:600;display:inline-flex;align-items:center;gap:4px;}"
            + ".retry-no{color:var(--text-muted);display:inline-flex;align-items:center;gap:4px;}"
            + ".retry-yes::before{content:'✓';font-weight:700;}"
            + ".retry-no::before{content:'✗';font-weight:700;}"
            + ".version{font-family:ui-monospace,SFMono-Regular,'SF Mono',Menlo,Consolas,monospace;font-size:12px;color:var(--text-muted);}"
            + ".detail-card{background:var(--surface);border:1px solid var(--border);border-radius:var(--radius-lg);padding:32px 36px;box-shadow:var(--shadow-md);}"
            + ".detail-header{display:flex;align-items:center;gap:12px;flex-wrap:wrap;margin-bottom:16px;}"
            + ".detail-title{margin:0 0 8px;font-size:30px;font-weight:600;letter-spacing:-.5px;}"
            + ".detail-meaning{margin:0 0 28px;color:var(--text-muted);font-size:15px;line-height:1.6;}"
            + ".detail-meta{margin:0;display:grid;gap:16px;}"
            + ".detail-meta .row{display:grid;grid-template-columns:140px 1fr;gap:18px;align-items:start;padding-bottom:14px;border-bottom:1px solid var(--border-muted);}"
            + ".detail-meta .row:last-child{padding-bottom:0;border-bottom:0;}"
            + ".detail-meta dt{color:var(--text-muted);font-weight:500;font-size:13px;padding-top:3px;}"
            + ".detail-meta dd{margin:0;font-size:14px;line-height:1.6;}"
            + ".detail-meta dd code{background:rgba(175,184,193,.2);padding:2px 6px;border-radius:3px;font-family:ui-monospace,SFMono-Regular,'SF Mono',Menlo,Consolas,monospace;font-size:12px;}"
            + ".not-found{background:var(--danger-bg);border:1px solid var(--danger-border);color:var(--danger);padding:14px 18px;border-radius:var(--radius);margin-bottom:20px;font-size:14px;}"
            + ".not-found code{background:rgba(255,255,255,.5);padding:2px 6px;border-radius:3px;font-family:ui-monospace,SFMono-Regular,'SF Mono',Menlo,Consolas,monospace;font-size:13px;font-weight:600;}"
            + ".back-link{display:inline-flex;align-items:center;gap:6px;margin-top:24px;color:var(--primary);text-decoration:none;font-size:14px;font-weight:500;padding:8px 14px;border:1px solid var(--border);border-radius:var(--radius);background:var(--surface);transition:background .15s;}"
            + ".back-link:hover{background:var(--surface-alt);text-decoration:none;}"
            + "@media (max-width:720px){"
            + ".container{padding:16px;}"
            + ".topbar{padding:10px 16px;flex-wrap:wrap;gap:8px;}"
            + ".brand-sub{display:none;}"
            + ".error-table th,.error-table td{padding:8px 10px;font-size:12px;}"
            + ".error-table .col-version,.error-table .col-investigate{display:none;}"
            + ".detail-card{padding:20px 18px;}"
            + ".detail-title{font-size:22px;}"
            + ".detail-meta .row{grid-template-columns:1fr;gap:4px;}"
            + "}"
            + "[dir=rtl] .error-table th,[dir=rtl] .error-table td{text-align:right;}"
            + "[dir=rtl] .detail-meta .row{direction:rtl;}"
            + "[dir=rtl] .breadcrumb{direction:rtl;}"
            + "[dir=rtl] .detail-header{direction:rtl;}";

    /**
     * 共享 HTML 头（含 viewport、CSS、品牌顶栏、语言切换器）。
     *
     * @param title    &lt;title&gt; 内容（已 escape）
     * @param langTag  BCP 47 语言标签（已 escape）
     * @param switcher 渲染好的语言切换器 HTML
     * @return 拼接好的 head + body 开头
     */
    private static String renderHead(String title, String langTag, String switcher) {
        String dir = isRtlLocale(langTag) ? "rtl" : "ltr";
        return "<!DOCTYPE html>\n"
                + "<html lang=\"" + escape(langTag) + "\" dir=\"" + dir + "\">\n"
                + "<head>\n"
                + "  <meta charset=\"UTF-8\">\n"
                + "  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n"
                + "  <title>" + escape(title) + "</title>\n"
                + "  <style>" + CSS + "</style>\n"
                + "</head>\n"
                + "<body>\n"
                + "  <header class=\"topbar\">\n"
                + "    <div class=\"brand\">\n"
                + "      <span class=\"brand-mark\">R</span>\n"
                + "      <span class=\"brand-name\">Atlas Richie Gateway</span>\n"
                + "      <span class=\"brand-sub\">Error Codes</span>\n"
                + "    </div>\n"
                + "    " + switcher + "\n"
                + "  </header>\n";
    }

    /**
     * RTL locale 集合（lang tag 前缀匹配）。这些 locale 渲染时需要
     * 在 &lt;html dir&gt;、表格对齐、breadcrumb 方向上做 RTL 适配。
     */
    private static final java.util.Set<String> RTL_PREFIXES = java.util.Set.of(
            "ar", "he", "fa", "ur");

    /**
     * 判断 BCP 47 tag 是否需要 RTL 渲染。
     *
     * @param langTag BCP 47 tag（如 {@code "ar-SA"}）
     * @return 是 RTL locale 返回 {@code true}
     */
    private static boolean isRtlLocale(String langTag) {
        if (langTag == null || langTag.length() < 2) {
            return false;
        }
        String lang = langTag.substring(0, 2).toLowerCase();
        return RTL_PREFIXES.contains(lang);
    }

    /**
     * 渲染错误码 badge 的 CSS 类（按命名空间取色：AUTH/TENANT/RATE/UPSTREAM/SYSTEM/ROUTE/REQ）。
     *
     * @param errorCode 错误码字符串
     * @return CSS class 名（如 {@code "code-AUTH"}）
     */
    private static String codeBadgeClass(String errorCode) {
        if (errorCode == null) {
            return "code-REQ";
        }
        int dash = errorCode.indexOf('-', 3);
        String namespace = dash > 0 ? errorCode.substring(3, dash) : "";
        return "code-" + namespace;
    }

    /**
     * 渲染 HTTP 状态 pill。
     *
     * @param httpStatus HTTP 状态码
     * @return HTML（如 {@code <span class="status-pill status-4xx">401</span>}）
     */
    private static String renderStatusPill(int httpStatus) {
        String cls;
        if (httpStatus >= 500) {
            cls = "status-5xx";
        } else if (httpStatus >= 400) {
            cls = "status-4xx";
        } else if (httpStatus >= 200 && httpStatus < 300) {
            cls = "status-2xx";
        } else {
            cls = "status-4xx";
        }
        return "<span class=\"status-pill " + cls + "\">" + httpStatus + "</span>";
    }

    /**
     * 渲染可重试标记。
     *
     * @param retryable 是否可重试
     * @return HTML（如 {@code <span class="retry-yes">是</span>}）
     */
    private String renderRetry(boolean retryable, Locale locale) {
        String text = resolve(retryable ? KEY_RETRY_YES : KEY_RETRY_NO, locale);
        return "<span class=\"" + (retryable ? "retry-yes" : "retry-no") + "\">"
                + escape(text) + "</span>";
    }

    /**
     * 渲染面包屑。
     *
     * @param homeLabel 首页文案（已 escape 或原字符串由调用方 escape）
     * @param current   当前条目（已 escape）
     * @param link      首页链接 URL（已 escape）
     * @return HTML
     */
    private static String renderBreadcrumb(String homeLabel, String current, String link) {
        if (current == null) {
            return "<nav class=\"breadcrumb\"><span class=\"current\">" + escape(homeLabel) + "</span></nav>";
        }
        return "<nav class=\"breadcrumb\">"
                + "<a href=\"" + escape(link) + "\">" + escape(homeLabel) + "</a>"
                + "<span class=\"sep\">/</span>"
                + "<span class=\"current\">" + escape(current) + "</span>"
                + "</nav>";
    }

    // Ordered 标记使 RouterFunction Bean 在创建后可被 Gateway 早期识别
    @SuppressWarnings("unused")
    private int unusedOrdered() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
