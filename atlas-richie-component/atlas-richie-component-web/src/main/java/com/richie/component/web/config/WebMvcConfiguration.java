package com.richie.component.web.config;

import com.richie.contract.constant.GlobalConstants;
import com.richie.context.utils.spring.JwtUtils;
import com.richie.component.i18n.config.I18nProperties;
import com.richie.component.web.i18n.AcceptLanguageHeaderLocaleResolver;
import com.richie.component.web.i18n.ShopTimezoneProvider;
import com.richie.component.web.utils.WebUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.Nonnull;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.web.servlet.LocaleContextResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 平台WebMVC配置文件
 *
 * @author richie696
 * @version 1.0
 * @since 2022-10-09 15:32:00
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({WebMvcProperties.class, CorsConfig.class})
@ComponentScan("com.richie.component.web")
@RequiredArgsConstructor
public class WebMvcConfiguration implements WebMvcConfigurer {

    /** WebMVC 与 CORS 等配置属性 */
    protected final WebMvcProperties webMvcProperties;

    /** 店铺时区提供者（可选），用于按租户/店铺解析时区 */
    @Autowired(required = false)
    protected ShopTimezoneProvider shopTimezoneProvider;

    /** I18n 组件配置（可选），用于默认 Locale */
    @Autowired(required = false)
    protected I18nProperties i18nProperties;

    /**
     * 注册 CORS 映射（当 platform.component.web.cors.enable=true 时生效）。
     *
     * @param registry CORS 注册表
     */
    @Override
    public void addCorsMappings(@Nonnull CorsRegistry registry) {
        CorsConfig cors = webMvcProperties.getCors();
        if (cors.isEnable()) {
            String[] exposedHeaders = new String[]{
                    GlobalConstants.X_RD_REQUEST_EXTRA,
                    GlobalConstants.X_RD_REQUEST_LANGUAGE,
                    GlobalConstants.X_RD_REQUEST_TIMEZONE,
                    GlobalConstants.X_RD_REQUEST_SHOP_CODE,
                    GlobalConstants.X_RD_REQUEST_FLAG,
                    GlobalConstants.X_RD_REQUEST_SSO,
                    GlobalConstants.X_ACCESS_TOKEN,
                    GlobalConstants.X_TENANT_ID,
                    GlobalConstants.X_TIME_FORMAT_PATTERN,
                    GlobalConstants.X_REQUEST_ORIGIN_URI,
                    GlobalConstants.X_CANARY_VERSION,
                    GlobalConstants.X_CANARY_ENV,
                    GlobalConstants.X_CANARY_CATEGORY,
                    GlobalConstants.X_CANARY_ID
            };
            if (ArrayUtils.isNotEmpty(cors.getExposedHeaders())) {
                exposedHeaders = ArrayUtils.addAll(cors.getExposedHeaders(), exposedHeaders);
            }
            registry.addMapping(cors.getPathPattern())
                    .allowedOriginPatterns(cors.getAllowedOriginPatterns())
                    .allowedMethods(cors.getAllowedMethods())
                    .allowedHeaders(cors.getAllowedHeaders())
                    .exposedHeaders(exposedHeaders)
                    .allowCredentials(cors.getAllowCredentials())
                    .allowPrivateNetwork(cors.getAllowPrivateNetwork())
                    .maxAge(cors.getMaxAge());
        }
    }

    /**
     * 配置 HTTP 消息转换器（统一 JSON 等序列化）。
     *
     * @param builder HTTP 消息转换器构建器
     */
    @Override
    public void configureMessageConverters(@Nonnull HttpMessageConverters.ServerBuilder builder) {
        WebUtils.refreshHttpMessageConverter(builder);
    }

    //    @Override
    //    public void configureHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
    //        resolvers.add((request, response, handler, ex) -> {
    //            // 待O2O和FOH业务线统一以后再具体实现
    //            return new ModelAndView();
    //        } );
    //    }


    /**
     * 变动语言拦截器
     */
    @Bean
    @ConditionalOnClass(HttpServletRequest.class)
    public LocaleChangeInterceptor localeChangeInterceptor() {
        return new LocaleChangeInterceptor();
    }

    /**
     * 注册拦截器（如语言切换）。
     *
     * @param interceptorRegistry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry interceptorRegistry) {
        interceptorRegistry.addInterceptor(localeChangeInterceptor());
    }

    /**
     * locale解析器
     * 这个方法返回一个LocaleContextResolver对象，它用于解析请求的语言环境和时区。
     * 首先，它创建一个CookieLocaleResolver对象和一个AcceptLanguageHeaderLocaleResolver对象。
     * AcceptLanguageHeaderLocaleResolver对象设置了支持的语言环境列表。
     * CookieLocaleResolver对象的默认语言环境解析函数被设置为AcceptLanguageHeaderLocaleResolver的解析函数。
     * 然后，它配置了默认的时区函数。这个函数首先尝试从请求头中获取租户代码和店铺代码，然后使用这些代码作为键来查找店铺的时区。
     * 如果没有找到店铺的时区，它会尝试使用请求头中的Accept-Timezone的时区。
     * 如果还是没有找到，它会使用系统默认的时区。
     * 最后，这个方法返回配置好的CookieLocaleResolver对象。
     * <p>
     * Bean 名称固定为 {@code localeResolver}，与 Spring Boot 的
     * {@code WebMvcAutoConfiguration$EnableWebMvcConfiguration.localeResolver} 同名。
     * 运行时通过 {@code spring.main.allow-bean-definition-overriding=true} 覆盖默认实现；
     * Spring Boot 4 的 AOT 处理阶段不接受同名 Bean 重复注册，需确保本 {@code @Configuration}
     * 类的解析早于 WebMvcAutoConfiguration，使 {@code @ConditionalOnMissingBean} 生效。
     *
     * @return 配置好的LocaleContextResolver对象
     */
    @Bean(name = "localeResolver")
    @ConditionalOnClass(HttpServletRequest.class)
    public LocaleContextResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver();
        AcceptLanguageHeaderLocaleResolver headerLocaleResolver = new AcceptLanguageHeaderLocaleResolver();

        //设置支持的语言环境列表
        Set<Locale> locales;
        if (CollectionUtils.isEmpty(webMvcProperties.getSupportedLanguageTags())) {
            locales = new HashSet<>();
        } else {
            locales = webMvcProperties.getSupportedLanguageTags().stream().map(Locale::forLanguageTag).collect(Collectors.toSet());
        }
        locales.add(Locale.CHINA);
        locales.add(Locale.TAIWAN);
        locales.add(Locale.FRANCE);
        locales.add(Locale.GERMANY);
        locales.add(Locale.ITALY);
        locales.add(Locale.JAPAN);
        locales.add(Locale.KOREA);
        locales.add(Locale.UK);
        locales.add(Locale.US);
        locales.add(Locale.CANADA);
        locales.add(Locale.of("id", "ID"));//印度尼西亚
        headerLocaleResolver.setSupportedLocales(locales.stream().toList());

        // 设置默认 Locale
        // 优先使用 i18n 组件配置的 defaultLocale（如果 i18n 组件存在），
        // 否则使用 Locale.CHINA 作为默认值，与 i18n 组件保持一致
        Locale defaultLocale = (i18nProperties != null && i18nProperties.getDefaultLocale() != null)
                ? i18nProperties.getDefaultLocale()
                : Locale.CHINA;
        headerLocaleResolver.setDefaultLocale(defaultLocale);
        log.debug("设置默认 Locale: {}", defaultLocale);

        resolver.setDefaultLocaleFunction(headerLocaleResolver::resolveLocale);

        //配置时区函数
        resolver.setDefaultTimeZoneFunction((request) -> {
            //如果有租户，则添加租户ID的拼接
            String tenantId;
            String token = request.getHeader(JwtUtils.X_ACCESS_TOKEN);
            if (StringUtils.isBlank(token)) {
                tenantId = request.getHeader(GlobalConstants.X_TENANT_ID);
            } else {
                tenantId = JwtUtils.getArgument(token, "tenantId");
            }

            String key;
            String shopCode = request.getHeader(GlobalConstants.X_RD_REQUEST_SHOP_CODE);
            if (StringUtils.isBlank(tenantId)) {
                key = shopCode;
            } else {
                key = String.join(ShopTimezoneProvider.SPLIT_TOKEN, tenantId, shopCode);
            }

            //1. 先查找店铺的时区
            if (shopTimezoneProvider != null) {
                ZoneId zoneId = shopTimezoneProvider.getZoneIdByKey(key);
                if (zoneId != null) {
                    return TimeZone.getTimeZone(zoneId);
                }
            }
            //2. 如果没找到店铺的时区，就使用用户浏览器请求头的 Accept-Timezone的时区
            String acceptTimezone = request.getHeader(GlobalConstants.X_RD_REQUEST_TIMEZONE);
            if (StringUtils.isNotBlank(acceptTimezone)) {
                return TimeZone.getTimeZone(ZoneId.of(acceptTimezone));
            }

            //3. 最后使用系统默认的时区
            return TimeZone.getDefault();
        });
        return resolver;
    }

}
