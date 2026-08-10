/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package cn.richie696.component.web.core.config.mvc;

import cn.richie696.component.web.core.utils.WebUtils;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.http.converter.autoconfigure.ServerHttpMessageConvertersCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.HttpMessageConverters;

/**
 * Registers the platform Jackson 3 converter through Spring Boot 4's server
 * converter customization SPI.
 *
 * <p>Spring Boot 4 no longer expects applications to replace the MVC converter
 * list through the Jackson 2-era {@code WebMvcConfigurer} hook. Keeping this
 * bridge in auto-configuration makes the Long/BigInteger-as-string contract
 * apply to every Servlet application that imports web-core.</p>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({HttpMessageConverters.class, ServerHttpMessageConvertersCustomizer.class})
public class WebHttpMessageConvertersAutoConfiguration {

    @Bean
    ServerHttpMessageConvertersCustomizer webCoreMessageConvertersCustomizer() {
        return WebUtils::refreshHttpMessageConverter;
    }
}
