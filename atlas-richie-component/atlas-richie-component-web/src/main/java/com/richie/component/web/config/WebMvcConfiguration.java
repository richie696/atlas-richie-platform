package com.richie.component.web.config;

import com.richie.component.web.utils.WebUtils;
import com.richie.contract.constant.GlobalConstants;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

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

    /**
     * WebMVC 与 CORS 等配置属性
     */
    protected final WebMvcProperties webMvcProperties;

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

}
