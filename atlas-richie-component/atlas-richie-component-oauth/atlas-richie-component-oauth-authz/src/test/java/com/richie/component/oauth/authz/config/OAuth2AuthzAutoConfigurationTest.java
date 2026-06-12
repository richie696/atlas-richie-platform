package com.richie.component.oauth.authz.config;

import com.richie.component.oauth.authz.spi.AuthorizationCodeStore;
import com.richie.component.oauth.authz.support.DefaultAuthorizationCodeStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Annotation;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OAuth2AuthzAutoConfiguration 测试")
class OAuth2AuthzAutoConfigurationTest {

    @Test
    @DisplayName("AutoConfiguration 注解存在于类上")
    void autoConfigurationAnnotation_existsOnClass() {
        AutoConfiguration autoConfiguration = OAuth2AuthzAutoConfiguration.class
                .getAnnotation(AutoConfiguration.class);

        assertThat(autoConfiguration).isNotNull();
    }

    @Test
    @DisplayName("ComponentScan 注解指定正确的包路径")
    void componentScanAnnotation_specifiesCorrectPackage() {
        ComponentScan componentScan = OAuth2AuthzAutoConfiguration.class
                .getAnnotation(ComponentScan.class);

        assertThat(componentScan).isNotNull();
        assertThat(componentScan.value()).contains("com.richie.component.oauth.authz");
    }

    @Test
    @DisplayName("Import 注解引入 OAuth2AutoConfiguration")
    void importAnnotation_importsOAuth2AutoConfiguration() {
        Import importAnnotation = OAuth2AuthzAutoConfiguration.class
                .getAnnotation(Import.class);

        assertThat(importAnnotation).isNotNull();
        assertThat(importAnnotation.value()).isNotEmpty();
    }

    @Test
    @DisplayName("authorizationCodeStore Bean 方法返回 DefaultAuthorizationCodeStore 实例")
    void authorizationCodeStoreBean_returnsDefaultAuthorizationCodeStore() throws Exception {
        OAuth2AuthzAutoConfiguration config = new OAuth2AuthzAutoConfiguration();

        java.lang.reflect.Method beanMethod = OAuth2AuthzAutoConfiguration.class
                .getDeclaredMethod("authorizationCodeStore");

        assertThat(beanMethod).isNotNull();
        assertThat(beanMethod.getReturnType()).isEqualTo(AuthorizationCodeStore.class);

        AuthorizationCodeStore store = (AuthorizationCodeStore) beanMethod.invoke(config);

        assertThat(store).isInstanceOf(DefaultAuthorizationCodeStore.class);
    }

    @Test
    @DisplayName("类上有 @ComponentScan 注解")
    void classHasComponentScanAnnotation() {
        Annotation[] annotations = OAuth2AuthzAutoConfiguration.class.getAnnotations();

        boolean hasComponentScan = false;
        for (Annotation annotation : annotations) {
            if (annotation instanceof ComponentScan) {
                hasComponentScan = true;
                break;
            }
        }

        assertThat(hasComponentScan).isTrue();
    }
}
