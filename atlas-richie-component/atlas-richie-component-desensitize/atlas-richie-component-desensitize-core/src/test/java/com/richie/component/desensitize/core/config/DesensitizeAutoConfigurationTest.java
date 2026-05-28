package com.richie.component.desensitize.core.config;

import com.richie.component.desensitize.core.service.MaskingService;
import com.richie.component.desensitize.core.util.DesensitizeUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
/**
 * DesensitizeAutoConfigurationTest 测试类。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
class DesensitizeAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DesensitizeAutoConfiguration.class));

    @Test
    void shouldRegisterMaskingServiceAndUtils() {
        contextRunner
                .withPropertyValues(
                        "platform.component.desensitize.sensitive-keys.phone=PHONE"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(MaskingService.class);
                    assertThat(DesensitizeUtils.mask("13812348000",
                            com.richie.component.desensitize.core.model.MaskType.PHONE))
                            .isEqualTo("138****8000");
                });
    }

    @Test
    void disabledSkipsMasking() {
        contextRunner
                .withPropertyValues("platform.component.desensitize.enabled=false")
                .run(context -> {
                    MaskingService service = context.getBean(MaskingService.class);
                    assertThat(service.mask("13812348000",
                            com.richie.component.desensitize.core.model.MaskType.PHONE))
                            .isEqualTo("13812348000");
                });
    }
}
