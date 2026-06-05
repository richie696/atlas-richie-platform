package com.richie.component.desensitize.logging.config.support;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import({
        com.richie.component.desensitize.logging.config.LoggingDesensitizeAutoConfiguration.class,
})
public class DesensitizeIntegrationTestConfiguration {
}
