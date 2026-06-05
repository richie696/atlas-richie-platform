package com.richie.component.vector.config.support;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import({
        com.richie.component.vector.config.PostgresqlVectorAutoConfiguration.class,
})
public class VectorIntegrationTestConfiguration {
}
