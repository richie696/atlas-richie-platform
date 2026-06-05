package com.richie.component.storage.config.support;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import({
        com.richie.component.storage.config.SmbAutoConfiguration.class,
})
public class StorageIntegrationTestConfiguration {
}
