//package com.richie.component.web.config;
//
//
//import org.springframework.boot.web.embedded.undertow.UndertowServletWebServerFactory;
//import org.springframework.boot.web.server.WebServerFactoryCustomizer;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//import static io.undertow.UndertowOptions.*;
//
//@Configuration
//public class UndertowConfig {
//
//    @Bean
//    public WebServerFactoryCustomizer<UndertowServletWebServerFactory> undertowCustomizer() {
//        return factory -> factory.addBuilderCustomizers(builder -> builder
//                .setServerOption(ENABLE_HTTP2, true)
//                .setServerOption(HTTP2_SETTINGS_ENABLE_PUSH, true)
//                .setServerOption(HTTP2_SETTINGS_HEADER_TABLE_SIZE, 8192)
//        );
//    }
//}
