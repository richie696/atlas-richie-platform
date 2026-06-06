package com.richie.testing.spring;

import org.springframework.test.context.DynamicPropertyRegistry;

import java.util.List;

public final class DynamicPropertyRegistration {

    private DynamicPropertyRegistration() {
    }

    public static void applyPairs(DynamicPropertyRegistry registry, List<String> pairs) {
        for (String pair : pairs) {
            int eq = pair.indexOf('=');
            if (eq <= 0 || eq >= pair.length() - 1) {
                continue;
            }
            String key = pair.substring(0, eq);
            String value = pair.substring(eq + 1);
            registry.add(key, () -> value);
        }
    }
}
