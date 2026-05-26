package com.richie.component.i18n.resolver;

import com.richie.component.i18n.config.I18nProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 国际化服务接口实现类
 *
 * @author richie696
 * @version 1.0
 * @since 2021-10-01 18:18:17
 */
@Component
@RequiredArgsConstructor
public class DefaultI18nResolver implements I18nResolver {

    /** Spring 消息源，用于根据 key 与 Locale 解析文案 */
    private final MessageSource messageSource;
    /** 国际化组件配置 */
    private final I18nProperties properties;

    @Override
    public String get(String key, Object... args) {
        Locale locale = LocaleContextHolder.getLocale();
        // 如果 LocaleContextHolder 中没有设置 Locale（如在非 Web 环境中），
        // 则回退到配置的默认 Locale
        if (locale == null) {
            locale = properties.getDefaultLocale();
        }
        return get(locale, key, args);
    }

    @Override
    public String get(Locale locale, String key, Object... args) {
        // 如果传入的 locale 为 null，使用配置的默认 Locale
        if (locale == null) {
            locale = properties.getDefaultLocale();
        }
        var message = messageSource.getMessage(key, null, locale);
        if (args == null || args.length == 0) {
            return message;
        }
        return MessageFormatter.arrayFormat(message, args).getMessage();
    }
}
