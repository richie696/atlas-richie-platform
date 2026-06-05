package com.richie.testing.spring;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * 在 Spring 上下文刷新前注入集测属性。
 */
public final class SpringPropertyInitializer {

    private SpringPropertyInitializer() {
    }

    public static void applyIfAvailable(
            BooleanSupplier available,
            PropertyContributor contributor,
            ConfigurableApplicationContext context) {
        if (!available.getAsBoolean()) {
            return;
        }
        List<String> pairs = new ArrayList<>();
        contributor.contribute(pairs);
        TestPropertyValues.of(pairs.toArray(String[]::new)).applyTo(context.getEnvironment());
    }
}
