/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package cn.richie696.component.web.core.config.mvc;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Bridges the container-independent CORS properties into Spring MVC.
 *
 * <p>The web-core module owns the policy and every Servlet container (Tomcat,
 * Jetty, and future implementations) receives the same response headers. A
 * gateway deployment can turn this off with
 * {@code platform.component.web.cors.enabled=false} to avoid duplicate CORS
 * headers.</p>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnProperty(prefix = "platform.component.web.cors", name = "enabled",
        havingValue = "true")
public class WebCorsAutoConfiguration {

    @Bean(name = "webCoreCorsConfigurer")
    public WebMvcConfigurer webCoreCorsConfigurer(CorsProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins(properties.getAllowedOrigins())
                        .allowedMethods(properties.getAllowedMethods())
                        .allowedHeaders(properties.getAllowedHeaders())
                        .allowCredentials(properties.isAllowCredentials())
                        .maxAge(properties.getMaxAge());
            }
        };
    }
}
