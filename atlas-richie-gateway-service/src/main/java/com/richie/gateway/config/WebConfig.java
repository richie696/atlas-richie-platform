package com.richie.gateway.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.webflux.error.DefaultErrorAttributes;
import org.springframework.boot.webflux.error.ErrorAttributes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

/**
 * WebFlux Web配置类
 * <p>
 * 注意：此配置类中的 CORS 配置仅作用于 Gateway 自身的 Controller（如 /api/auth/**、/oauth2/** 等），
 * 不作用于 Gateway 路由转发的请求。路由转发的 CORS 配置需要在 application-gateway.yml 中的
 * spring.cloud.gateway.server.webflux.globalcors 配置。
 *
 * @author richie696
 */
@Configuration
public class WebConfig {

    @Bean
    @ConditionalOnMissingBean
    public ErrorAttributes errorAttributes() {
        return new DefaultErrorAttributes();
    }

    @Bean
    @ConditionalOnMissingBean
    public WebProperties.Resources resources() {
        return new WebProperties.Resources();
    }

    @Bean
    @ConditionalOnMissingBean
    public ServerCodecConfigurer serverCodecConfigurer() {
        return ServerCodecConfigurer.create();
    }

    /**
     * Gateway 自身 Controller 的 CORS 配置
     *
     * @return CorsWebFilter
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "spring.cloud.gateway.server.webflux.globalcors",
        name = "add-to-simple-url-handler-mapping",
        matchIfMissing = false  // 只有当 globalcors 配置存在时才启用
    )
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();

        // 开发环境全放通配置（与 Gateway 路由转发的 CORS 配置保持一致）
        corsConfig.setAllowedOriginPatterns(Collections.singletonList("*"));
        corsConfig.setAllowedHeaders(Collections.singletonList("*"));
        corsConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        corsConfig.setAllowCredentials(true);
        corsConfig.setExposedHeaders(Collections.singletonList("*"));
        corsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 配置对所有路径生效（Gateway 自身的 Controller）
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }
}
