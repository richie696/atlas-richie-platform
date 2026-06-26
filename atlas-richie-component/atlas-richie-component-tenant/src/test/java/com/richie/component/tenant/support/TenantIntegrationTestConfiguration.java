package com.richie.component.tenant.support;

import com.richie.component.tenant.config.TenantAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(TenantAutoConfiguration.class)
public class TenantIntegrationTestConfiguration {
}
