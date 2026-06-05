package com.richie.component.http.httpclient5.support;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import({
        com.richie.component.http.httpclient5.config.HttpAutoConfiguration.class,
})
public class HttpIntegrationTestConfiguration {
}
