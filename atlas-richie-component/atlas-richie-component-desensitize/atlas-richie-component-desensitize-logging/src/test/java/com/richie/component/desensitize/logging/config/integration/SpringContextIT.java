package com.richie.component.desensitize.logging.config.integration;

import com.richie.component.desensitize.logging.config.support.DesensitizeIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@DesensitizeIntegrationTest
class SpringContextIT {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }
}
