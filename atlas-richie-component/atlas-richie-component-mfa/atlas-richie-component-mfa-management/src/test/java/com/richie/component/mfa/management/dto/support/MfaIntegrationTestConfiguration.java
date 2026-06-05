package com.richie.component.mfa.management.dto.support;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import({
        com.richie.component.mfa.management.config.MfaManagementAutoConfiguration.class,
})
public class MfaIntegrationTestConfiguration {
}
