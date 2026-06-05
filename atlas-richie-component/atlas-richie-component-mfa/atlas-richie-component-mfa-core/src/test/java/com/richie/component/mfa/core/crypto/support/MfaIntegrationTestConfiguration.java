package com.richie.component.mfa.core.crypto.support;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import({
        com.richie.component.mfa.core.config.MfaAutoConfiguration.class,
})
public class MfaIntegrationTestConfiguration {
}
