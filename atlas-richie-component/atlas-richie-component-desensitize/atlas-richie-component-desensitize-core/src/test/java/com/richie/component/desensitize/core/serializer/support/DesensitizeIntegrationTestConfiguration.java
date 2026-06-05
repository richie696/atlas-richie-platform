package com.richie.component.desensitize.core.serializer.support;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import({
        com.richie.component.desensitize.core.config.DesensitizeAutoConfiguration.class,
})
public class DesensitizeIntegrationTestConfiguration {
}
