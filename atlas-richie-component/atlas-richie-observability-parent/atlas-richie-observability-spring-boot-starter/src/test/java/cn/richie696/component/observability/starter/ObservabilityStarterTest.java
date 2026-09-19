package cn.richie696.component.observability.starter;

import cn.richie696.component.observability.actuator.ObservabilityActuatorAutoConfiguration;
import cn.richie696.component.observability.autoconfigure.ObservabilityAutoConfiguration;
import cn.richie696.component.observability.core.ObservabilityState;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityStarterTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ObservabilityAutoConfiguration.class,
                    ObservabilityActuatorAutoConfiguration.class))
            .withPropertyValues("spring.application.name=starter-test");

    @Test
    void applicationEntryPointProvidesCoreStateAndActuatorConfiguration() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ObservabilityState.class);
            assertThat(context).hasBean("atlasRichieObservabilityMeterFilter");
        });
    }

    @Test
    void applicationSwitchDisablesEffectiveState() {
        contextRunner
                .withPropertyValues(
                        "atlas.observability.enabled=false",
                        "otel.sdk.disabled=true")
                .run(context -> assertThat(context.getBean(ObservabilityState.class).disabled())
                        .isTrue());
    }
}
