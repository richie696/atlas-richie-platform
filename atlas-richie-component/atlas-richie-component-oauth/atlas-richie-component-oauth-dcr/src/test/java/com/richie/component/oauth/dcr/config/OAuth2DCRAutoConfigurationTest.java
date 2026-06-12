package com.richie.component.oauth.dcr.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OAuth2DCRAutoConfiguration 测试")
class OAuth2DCRAutoConfigurationTest {

    @Test
    @DisplayName("类上标注 @AutoConfiguration")
    void classHasAutoConfigurationAnnotation() {
        OAuth2DCRAutoConfiguration configuration = new OAuth2DCRAutoConfiguration();

        assertThat(configuration.getClass().isAnnotationPresent(AutoConfiguration.class)).isTrue();
    }

    @Test
    @DisplayName("类上标注 @ComponentScan")
    void classHasComponentScanAnnotation() {
        ComponentScan annotation = OAuth2DCRAutoConfiguration.class.getAnnotation(ComponentScan.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains("com.richie.component.oauth.dcr");
    }

    @Test
    @DisplayName("类上标注 @Import 导入 OAuth2AutoConfiguration")
    void classHasImportAnnotation() {
        Import annotation = OAuth2DCRAutoConfiguration.class.getAnnotation(Import.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).hasSize(1);
        assertThat(annotation.value()[0].getName())
                .isEqualTo("com.richie.component.oauth.core.config.OAuth2AutoConfiguration");
    }

    @Test
    @DisplayName("默认构造函数可以创建实例")
    void defaultConstructor_createsInstance() {
        OAuth2DCRAutoConfiguration configuration = new OAuth2DCRAutoConfiguration();

        assertThat(configuration).isNotNull();
    }
}
