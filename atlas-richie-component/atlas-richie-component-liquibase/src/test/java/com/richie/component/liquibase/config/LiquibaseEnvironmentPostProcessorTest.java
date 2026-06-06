package com.richie.component.liquibase.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class LiquibaseEnvironmentPostProcessorTest {

    private final LiquibaseEnvironmentPostProcessor processor = new LiquibaseEnvironmentPostProcessor();

    @Test
    void postProcessEnvironment_setsDefaultWhenPropertyMissing() {
        MockEnvironment environment = new MockEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.liquibase.enabled")).isEqualTo("false");
    }

    @Test
    void postProcessEnvironment_doesNotOverrideExplicitValue() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.liquibase.enabled", "true");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.liquibase.enabled")).isEqualTo("true");
    }

    @Test
    void getOrder_isHighestPrecedence() {
        assertThat(processor.getOrder()).isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);
    }
}
