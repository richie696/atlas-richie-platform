package com.richie.component.http.restclient.support;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import({
        com.richie.component.http.restclient.config.HttpAutoConfiguration.class,
})
public class HttpIntegrationTestConfiguration {
}
