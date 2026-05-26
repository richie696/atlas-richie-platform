package com.richie.component.i18n.resolver;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 国际化服务接口
 *
 * @author richie696
 * @version 1.0
 * @since 2023-10-06 23:14:58
 */
@Component
public class I18n {

    /** 静态持有的国际化解析器，由 Spring 注入后赋值 */
    private static I18nResolver i18nResolver;

    /**
     * 构造方法
     * @param i18nResolver 国际化对象
     */
    public I18n(I18nResolver i18nResolver) {
        I18n.i18nResolver = i18nResolver;
    }

    /**
     * 根据国际化资源键和参数获取国际化资源文本的方法
     *
     * @param key 国际化资源键
     * @param args 国际化资源文本动态参数
     * @return 返回国际化文本
     */
    public static String get(String key, Object... args) {
        return i18nResolver.get(key, args);
    }

    /**
     * 根据自定义区域和国际化资源键和参数获取国际化资源文本的方法
     *
     * @param locale 自定义区域
     * @param key 国际化资源键
     * @param args 国际化资源文本动态参数
     * @return 返回国际化文本
     */
    public static String get(Locale locale, String key, Object... args) {
        return i18nResolver.get(locale, key, args);
    }

}
