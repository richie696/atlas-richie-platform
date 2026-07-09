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
