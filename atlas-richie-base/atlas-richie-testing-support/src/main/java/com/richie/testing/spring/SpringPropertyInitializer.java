/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
