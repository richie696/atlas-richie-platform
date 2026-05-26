package com.richie.gateway.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger文档配置类
 *
 * @author richie696
 * @version 1.0
 * @since 2023-08-29 22:12:50
 */
@OpenAPIDefinition(
        info = @Info(
                title = "润典通用网关接口",
                version = "3.0",
                description = "更多请咨询服务开发者richie696。",
                contact = @Contact(
                        name = "richie696",
                        url = "https://blog.csdn.net/richie696",
                        email = "richie696@live.com"
                )
        )
)
@Configuration
public class SwaggerConfig {

    @Bean
    public GroupedOpenApi gatewayApi() {
        return GroupedOpenApi.builder().group("gateway").displayName("通用网关服务")
                .addOpenApiCustomizer(openApi -> openApi.info(new io.swagger.v3.oas.models.info.Info().title("网关服务 API").version("3.0.0")))
                .packagesToScan("com.richie.gateway.feign")
                .pathsToMatch("/token/**")
                .build();
    }
}
