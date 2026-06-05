package com.richie.component.http.jdk.support;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import({
        com.richie.component.http.jdk.config.HttpAutoConfiguration.class,
})
public class HttpIntegrationTestConfiguration {
}
