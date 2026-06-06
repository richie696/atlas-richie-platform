package com.richie.component.vector.config.support;

import com.richie.component.vector.config.VectorProperties;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootConfiguration
@EnableConfigurationProperties(VectorProperties.class)
public class VectorIntegrationTestConfiguration {
}
