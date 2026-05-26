package com.richie.component.web.i18n;

import com.richie.contract.constant.GlobalConstants;
import com.richie.component.web.i18n.tomcat.AcceptLanguage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 基于请求头解析 Locale，优先使用 X_RD_REQUEST_LANGUAGE，其次默认 Locale，再其次 Accept-Language。
 *
 * @author yuy
 * @version 1.0
 * @since 2023-10-20 16:11:04
 */
@Slf4j
public class AcceptLanguageHeaderLocaleResolver extends AcceptHeaderLocaleResolver {

    /**
     * 此方法修改了头解析顺序，旧的是以Accept-Language优先解析，现在是以X_RD_REQUEST_LANGUAGE优先解析
     * 前端传过来的头都是zh-CN,zh;q=0.9,en;q=0.8,en-US;q=0.7这样的，以中划线作为分隔符，不要跟后台spring国际化文件的zh_CN混淆了
     * <p>
     * Locale 解析优先级：
     * 1. 请求头 X_RD_REQUEST_LANGUAGE（如果存在且有效）
     * 2. 配置的默认 Locale（defaultLocale）
     * 3. 请求的 Locale（request.getLocale()，来自 Accept-Language 头）
     * <p>
     * 此方法用于根据请求解析语言环境。
     * 它首先检查请求头中的'X_RD_REQUEST_LANGUAGE'属性。
     * 如果头为空或解析失败，优先返回配置的默认语言环境，而不是请求的语言环境。
     * 然后尝试解析头以获取接受的语言列表及其语言环境。
     * 如果支持的语言环境列表为空或包含请求的语言环境，则返回请求的语言环境。
     * 如果只有一种语言，它使用请求的语言环境来匹配支持的语言环境。
     * 如果找到匹配的语言环境，则返回该语言环境。
     * 如果找不到匹配的语言环境，它返回默认语言环境（如果不为null），否则返回请求的语言环境。
     *
     * @param request 从中解析语言环境的HttpServletRequest对象。
     * @return 解析的Locale对象。
     */
    @Nonnull
    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        var header = request.getHeader(GlobalConstants.X_RD_REQUEST_LANGUAGE);
        var defaultLocale = getDefaultLocale();
        
        // 如果请求头为空，优先使用配置的默认 Locale
        if (StringUtils.isBlank(header)) {
            if (defaultLocale != null) {
                return defaultLocale;
            }
            // 只有在 defaultLocale 为 null 时才使用 request.getLocale()
            return request.getLocale();
        }
        
        // 尝试解析请求头中的语言环境
        Locale requestLocale = null;
        List<Locale> requestLocales = Collections.emptyList();
        try {
            var acceptLanguages = AcceptLanguage.parse(new StringReader(header));
            requestLocale = acceptLanguages.getFirst().getLocale();
            requestLocales = acceptLanguages.stream().map(AcceptLanguage::getLocale).toList();
        } catch (IOException e) {
            log.error("解析语言环境头失败: {}，错误: {}", header, e.getMessage());
        }
        
        // 如果解析失败，优先使用配置的默认 Locale
        if (requestLocale == null) {
            log.warn("无法从头解析语言环境: {}，将使用配置的默认语言环境。", header);
            if (defaultLocale != null) {
                return defaultLocale;
            }
            // 只有在 defaultLocale 为 null 时才使用 request.getLocale()
            return request.getLocale();
        }
        
        // 支持的语言列表
        var supportedLocales = getSupportedLocales();

        // 如果支持的语言列表为空，或者请求的语言在支持列表中，直接返回
        if (supportedLocales.isEmpty() || supportedLocales.contains(requestLocale)) {
            return requestLocale;
        }

        // 尝试在请求的语言列表中找到支持的语言（按权重顺序）
        var supportedLocale = findSupportedLocale(requestLocales, supportedLocales);
        if (supportedLocale != null) {
            return supportedLocale;
        }
        
        // 如果找不到匹配的语言，优先返回配置的默认 Locale
        return (defaultLocale != null ? defaultLocale : requestLocale);
    }

    /**
     * 在请求语言列表中按权重找到第一个在支持列表中的 Locale（先语言匹配，再语言+地区匹配）。
     *
     * @param requestLocales   请求中的语言列表（含权重）
     * @param supportedLocales 支持的语言列表
     * @return 匹配到的 Locale，未找到为 null
     */
    @Nullable
    private Locale findSupportedLocale(List<Locale> requestLocales, List<Locale> supportedLocales) {
        for (var requestLocale : requestLocales) {
            // 先仅语言匹配
            for (var supportedLocale : supportedLocales) {
                if (Strings.CS.equals(supportedLocale.getLanguage(), requestLocale.getLanguage())) {
                    return supportedLocale;
                }
            }
            // 找不到语言匹配的，再找语言+地区匹配，有可能匹配到en-US
            if (supportedLocales.contains(requestLocale)) {
                return requestLocale;
            }
        }
        return null;
    }

}
