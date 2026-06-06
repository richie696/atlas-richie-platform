package com.richie.component.search.support;

import com.richie.component.search.config.SearchAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(SearchAutoConfiguration.class)
public class SearchIntegrationTestConfiguration {
}
