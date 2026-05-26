package com.richie.component.i18n.config;

import com.richie.component.i18n.aspect.I18nDictAspect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

/**
 * 国际化组件自动配置类
 *
 * @author richie696
 * @version 1.0
 * @since 2023-09-05 10:39:15
 */
@Slf4j
@Configuration
@ComponentScan("com.richie.component.i18n")
@EnableConfigurationProperties({I18nProperties.class})
public class I18nAutoConfiguration {

    /**
     * 默认构造函数（供 Spring 使用）。
     */
    public I18nAutoConfiguration() {
    }

    /**
     * 注册国际化消息源 Bean。
     *
     * @param properties 国际化配置属性
     * @return 配置好的 ResourceBundleMessageSource
     */
    @Bean
    public ResourceBundleMessageSource messageSource(I18nProperties properties) {
        // 验证配置，确保 defaultLocale 不为 null
        if (properties.getDefaultLocale() == null) {
            log.warn("defaultLocale 未配置，将使用 Locale.CHINA 作为默认值");
            properties.setDefaultLocale(Locale.CHINA);
        }

        // 注意：不设置 JVM 级别的默认 Locale（Locale.setDefault），
        // 只设置 ResourceBundleMessageSource 的默认 Locale，避免影响其他代码
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames(properties.getPath());
        source.setUseCodeAsDefaultMessage(true);
        source.setDefaultEncoding(properties.getEncoding());
        source.setDefaultLocale(properties.getDefaultLocale());
        return source;
    }

    /**
     * 注册国际化字典注入切面 Bean（需配置 enable-aspect-i18n=true）。
     *
     * @param properties 国际化配置属性
     * @return I18nDictAspect 实例
     */
    @Bean
    @ConditionalOnProperty(prefix = "platform.component.i18n", name = "enable-aspect-i18n", havingValue = "true")
    public I18nDictAspect i18nDictAspect(I18nProperties properties) {
        return new I18nDictAspect(properties);
    }

}
