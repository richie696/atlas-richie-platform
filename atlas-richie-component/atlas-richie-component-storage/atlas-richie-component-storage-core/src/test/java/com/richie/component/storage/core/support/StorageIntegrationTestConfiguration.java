package com.richie.component.storage.core.support;

import com.richie.component.storage.config.StorageProperties;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@EnableConfigurationProperties(StorageProperties.class)
@Import({})
public class StorageIntegrationTestConfiguration {
}
